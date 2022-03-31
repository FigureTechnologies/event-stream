package io.provenance.eventstream

import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.extensions.awaitShutdown
import io.provenance.eventstream.net.defaultOkHttpClient
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.historicalBlockMetaData
import io.provenance.eventstream.stream.mapHistoricalHeaderData
import io.provenance.eventstream.stream.mapLiveHeaderData
import io.provenance.eventstream.stream.metadataFlow
import io.provenance.eventstream.stream.nodeEventStream
import io.provenance.eventstream.stream.rpc.response.MessageType.NewBlockHeader
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

fun main() = runBlocking {
    val log = KotlinLogging.logger {}
    val host = "rpc.test.provenance.io:443"
    val okHttp = defaultOkHttpClient()
    val netAdapter = okHttpNetAdapter(host, okHttp, tls = true)
    val decoderAdapter = moshiDecoderAdapter()

    // Example is not collected.
    historicalBlockMetaData(netAdapter, 1, 100)
        .mapHistoricalHeaderData()
        .onEach { if (it.height % 1500 == 0L) { log.info { "oldBlock: ${it.height}" } } }

    // Example is not collected.
    nodeEventStream<NewBlockHeader>(netAdapter, decoderAdapter)
        .mapLiveHeaderData()
        .onEach { println("liveBlock: ${it.height}") }

    // Use metadataFlow to fetch from:(current - 10000) to:(current + 5).
    // This will combine the historical flow and live flow to create an ordered stream of BlockHeaders.
    val current = netAdapter.rpcAdapter.getCurrentHeight()!!
    metadataFlow(netAdapter, decoderAdapter, from = current - 10000, to = current + 5)
        .collect { println("recv:${it.height}") }

    okHttp.awaitShutdown()
}
