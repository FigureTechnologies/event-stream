package io.provenance.eventstream

import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.flows.blockDataFlow
import io.provenance.eventstream.stream.flows.blockHeaderFlow
import io.provenance.eventstream.stream.flows.historicalBlockDataFlow
import io.provenance.eventstream.stream.flows.historicalBlockHeaderFlow
import io.provenance.eventstream.stream.flows.liveBlockDataFlow
import io.provenance.eventstream.stream.flows.liveBlockHeaderFlow
import io.provenance.eventstream.stream.flows.nodeEventStream
import io.provenance.eventstream.stream.rpc.response.MessageType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

fun main() = runBlocking {
    val log = KotlinLogging.logger {}
    val host = "http://localhost:26657"
    val netAdapter = okHttpNetAdapter(host)
    val decoderAdapter = moshiDecoderAdapter()

    // Example is not collected.
    historicalBlockHeaderFlow(netAdapter, 6278600, 6278900)
        .onEach { if (it.height % 1500 == 0L) { log.info { "oldHeader: ${it.height}" } } }

    // Example is not collected.
    historicalBlockDataFlow(netAdapter, 6278600, 6278900)
        .onEach { if (it.height % 1500 == 0L) { log.info { "oldBlock: ${it.height}" } } }

    // Example is not collected.
    liveBlockHeaderFlow(netAdapter, decoderAdapter)
        .onEach { println("liveHeader: ${it.height}") }

    // Example is not collected.
    liveBlockDataFlow(netAdapter, decoderAdapter)
        .onEach { println("liveBlock: $it") }

    // Example is not collected.
    nodeEventStream<MessageType.NewBlock>(netAdapter, decoderAdapter)
        .onEach { println("liveBlock: $it") }

    // Use metadataFlow to fetch from:(current - 10000) to:(current).
    // This will combine the historical flow and live flow to create an ordered stream of BlockHeaders.
    // Example is not collected.
    val current = netAdapter.rpcAdapter.getCurrentHeight()!!
    blockHeaderFlow(netAdapter, decoderAdapter, from = current - 1000, to = current)
        .onEach { println("recv:${it.height}") }

    // Use metadataFlow to fetch from:(current - 10000) to:(current).
    // This will combine the historical flow and live flow to create an ordered stream of BlockHeaders.
    // Example is not collected.
    blockDataFlow(netAdapter, decoderAdapter, from = current - 1000, to = current)
        .onEach { println("recv:$it") }
        .collect()

    netAdapter.shutdown()
}
