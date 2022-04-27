package io.provenance.eventstream.stream.flows

import io.provenance.eventstream.net.NetAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicLong

/**
 *
 */
internal fun currentHeightFn(netAdapter: NetAdapter): suspend () -> Long? =
    { netAdapter.rpcAdapter.getCurrentHeight() }

/**
 *
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> combinedFlow(
    getCurrentHeight: suspend () -> Long?,
    from: Long? = null,
    to: Long? = null,
    getHeight: (T) -> Long,
    historicalFlow: (from: Long, to: Long) -> Flow<T>,
    liveFlow: () -> Flow<T>,
): Flow<T> = channelFlow {
    val log = KotlinLogging.logger {}
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
