package io.provenance.eventstream.stream.flows

import io.provenance.eventstream.decoder.DecoderAdapter
import io.provenance.eventstream.net.NetAdapter
import io.provenance.eventstream.stream.models.BlockHeader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicLong

/**
 * Create a [Flow] of [BlockHeader] from height to height.
 *
 * This flow will intelligently determine how to merge the live and history flows to
 * create a seamless stream of [BlockHeader] objects.
 *
 * @param netAdapter The [NetAdapter] to use for network interfacing.
 * @param decoderAdapter The [DecoderAdapter] to use to marshal json.
 * @param from The `from` height, if omitted, height 1 is used.
 * @param to The `to` height, if omitted, no end is assumed.
 * @return The [Flow] of [BlockHeader].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun metadataFlow(netAdapter: NetAdapter, decoderAdapter: DecoderAdapter, from: Long? = null, to: Long? = null): Flow<BlockHeader> = channelFlow {
    val parent = this
    val log = KotlinLogging.logger {}
    val channel = Channel<BlockHeader>(capacity = 10_000) // buffer for: 10_000 * 6s block time / 60s/m / 60m/h == 16 2/3 hrs buffer time.
    val liveJob = launch {
        liveMetadataFlow(netAdapter, decoderAdapter)
            .buffer()
            .collect { channel.send(it) }
    }
    val current = netAdapter.rpcAdapter.getCurrentHeight()!!
    log.debug { "hist//live split point:$current" }

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
    val lastSeen = AtomicLong(0)
    if (needHist) {
        historicalMetadataFlow(netAdapter, from ?: 1, current)
            .collect {
                send(it)
                lastSeen.set(it.height)
            }
    }

    // Live flow. Skip any dupe blocks.
    if (needLive) {
        var firstLive = channel.receive()
        var lastCurrent = current

        // Determine if we have a gap between the last seen current and first received that needs to be filled.
        // If fetching and emitting the gap takes longer that the time to fetch a new block, loop and do it again
        // until the next block to be emitted is the head of the channel.
        do {
            log.debug { "gap fill from ${lastCurrent + 1} to ${firstLive.height}" }
            historicalMetadataFlow(netAdapter, lastCurrent + 1, firstLive.height)
                .collect { block ->
                    // Update the lastSeen height after successful send.
                    select {
                        onSend(block) {
                            lastSeen.set(block.height)
                        }
                    }
                }
            lastCurrent = firstLive.height
            firstLive = channel.receive()
        } while (lastCurrent + 1 < firstLive.height)

        // Send the last known head of the channel.
        send(firstLive)
        lastSeen.set(firstLive.height)

        // Continue receiving everything else live.
        // Drop anything between current head and the last fetched history record.
        channel.receiveAsFlow().dropWhile {
            it.height <= lastSeen.get()
        }.collect { block ->
            select {
                onSend(block) {
                    if (to != null && block.height >= to) {
                        parent.close()
                    }
                }
            }
        }
    }
}
