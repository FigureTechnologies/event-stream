package io.provenance.eventstream

import io.provenance.eventstream.decoder.DecoderAdapter
import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.extensions.awaitShutdown
import io.provenance.eventstream.net.NetAdapter
import io.provenance.eventstream.net.defaultOkHttpClient
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.historicalBlockMetaData
import io.provenance.eventstream.stream.mapHistoricalHeaderData
import io.provenance.eventstream.stream.mapLiveHeaderData
import io.provenance.eventstream.stream.models.BlockHeader
import io.provenance.eventstream.stream.nodeEventStream
import io.provenance.eventstream.stream.rpc.response.MessageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicLong

fun main() = runBlocking {
    val log = KotlinLogging.logger {}
    val host = "rpc.test.provenance.io:443"
    val okHttp = defaultOkHttpClient()
    val netAdapter = okHttpNetAdapter(host, okHttp, tls = true)
    val decoderAdapter = moshiDecoderAdapter()

    // historicalBlockMetaData(netAdapter, 1, 100)
    //     .mapHistoricalHeaderData()
    //     .onEach { if (it.height % 1500 == 0L) { log.info { "oldBlock: ${it.height}" } } }

    // nodeEventStream<MessageType.NewBlockHeader>(netAdapter, decoderAdapter)
    //     .mapLiveHeaderData()
    //     .onEach { println("liveBlock: ${it.height}") }

    val current = netAdapter.rpcAdapter.getCurrentHeight()!!
    metadataFlow(netAdapter, decoderAdapter, from = current - 10000, to = current + 5)
        .collect { println("recv:${it.height}") }

    okHttp.awaitShutdown()
}

/**
 *
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun metadataFlow(netAdapter: NetAdapter, decoderAdapter: DecoderAdapter, from: Long? = null, to: Long? = null): Flow<BlockHeader> = channelFlow {
    val parent = this
    val log = KotlinLogging.logger {}
    val channel = Channel<BlockHeader>(capacity = 10_000) // buffer for: 10_000 * 6s block time / 60s/m / 60m/h == 16 2/3 hrs buffer time.
    val liveJob = launch {
        nodeEventStream<MessageType.NewBlockHeader>(netAdapter, decoderAdapter)
            .mapLiveHeaderData()
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
        historicalBlockMetaData(netAdapter, from ?: 1, current)
            .mapHistoricalHeaderData()
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
            historicalBlockMetaData(netAdapter, lastCurrent + 1, firstLive.height)
                .mapHistoricalHeaderData()
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

        } while (lastCurrent+1 < firstLive.height)

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