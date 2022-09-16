package tech.figure.eventstream.stream.flows

import tech.figure.eventstream.stream.infrastructure.ServerException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import mu.KotlinLogging
import tech.figure.eventstream.net.NetAdapter
import tech.figure.eventstream.utils.backoff
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.ExperimentalTime

val log = KotlinLogging.logger {}

/**
 *
 */
internal fun currentHeightFn(netAdapter: NetAdapter): suspend () -> Long? =
    { netAdapter.rpcAdapter.getCurrentHeight() }

/**
 *
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
internal fun <T> combinedFlow(
    getCurrentHeight: suspend () -> Long?,
    from: Long? = null,
    to: Long? = null,
    getHeight: (T) -> Long,
    historicalFlow: (from: Long, to: Long) -> Flow<T>,
    liveFlow: () -> Flow<T>,
): Flow<T> = channelFlow<T> {
    val channel = Channel<T>(capacity = 10_000) // buffer for: 10_000 * 6s block time / 60s/m / 60m/h == 16 2/3 hrs buffer time.
    val liveJob = async(coroutineContext) {
        liveFlow()
            .catch { channel.close(it) }
            .buffer(UNLIMITED)
            .collect { channel.send(it) }
    }

    val current = getCurrentHeight()!!

    // Determine if we need live data.
    // ie: if to is null, or more than current,
    val needLive = to == null || to > current

    // Determine if we need historical data.
    // ie: if from is null, or less than current, then we do.
    val needHist = from == null || from < current
    log.debug { "from:$from to:$to current:$current needHist:$needHist needLive:$needLive" }

    // Cancel live job and channel if unneeded.
    if (!needLive) {
        liveJob.cancel()
        channel.close()
    }

    // Process historical stream if needed.
    if (needHist) {
        val historyFrom = from ?: 1
        log.debug { "processing historical flow from:$historyFrom to:$current" }
        historicalFlow(historyFrom, current).collect {
            log.trace { "sending historical: ${getHeight(it)}" }
            send(it)
            getHeight(it).also { h ->
                if (to != null && h >= to) {
                    close()
                }
            }
        }
    }

    // Live flow. Skip any dupe blocks.
    if (needLive) {
        log.debug { "processing live flow to:$to" }

        // Continue receiving everything else live.
        // Drop anything between current head and the last fetched history record.
        val lastSeen = AtomicLong(0)
        channel.receiveAsFlow().collect { block ->
            log.trace { "got pending block ${getHeight(block)}" }
            send(block)
            getHeight(block).also { h ->
                lastSeen.set(h)
                if (to != null && h >= to) {
                    close()
                }
            }
        }

        while (!channel.isClosedForReceive) {
            val block = channel.receiveCatching().getOrThrow()
            val height = getHeight(block)

            log.trace { "onReceive:$height" }

            // Skip if we have seen it already.
            if (height <= lastSeen.get()) {
                log.trace { "already seen $height, skipping" }
                return@channelFlow
            }

            send(block)
            getHeight(block).also { h ->
                lastSeen.set(h)
                if (to != null && h >= to) {
                    close()
                }
            }
        }
    }
}
    .cancellable()
    .retryWhen { cause: Throwable, attempt: Long ->
        log.warn("flow::error; recovering Flow (attempt ${attempt + 1})")
        when (cause) {
            is EOFException,
            is CompletionException,
            is ConnectException,
            is SocketTimeoutException,
            is ServerException, // 502 Bad Gateway
            is SocketException -> {
                val duration = backoff(attempt, jitter = false)
                log.error("Reconnect attempt #$attempt; waiting ${duration.inWholeSeconds}s before trying again: $cause")
                delay(duration)
                true
            }
            else -> false
        }
    }
