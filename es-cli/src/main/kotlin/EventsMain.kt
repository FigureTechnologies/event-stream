package io.provenance.eventstream

import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.flows.historicalMetadataFlow
import io.provenance.eventstream.stream.flows.liveMetadataFlow
import io.provenance.eventstream.stream.flows.metadataFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

fun main() = runBlocking {
    val log = KotlinLogging.logger {}
    val host = "rpc.test.provenance.io:443"
    val netAdapter = okHttpNetAdapter(host, tls = true)
    val decoderAdapter = moshiDecoderAdapter()

    // Example is not collected.
    historicalMetadataFlow(netAdapter, 1, 100)
        .onEach { if (it.height % 1500 == 0L) { log.info { "oldBlock: ${it.height}" } } }

    // Example is not collected.
    liveMetadataFlow(netAdapter, decoderAdapter)
        .onEach { println("liveBlock: ${it.height}") }

    // Use metadataFlow to fetch from:(current - 10000) to:(current + 5).
    // This will combine the historical flow and live flow to create an ordered stream of BlockHeaders.
    val current = netAdapter.rpcAdapter.getCurrentHeight()!!
    metadataFlow(netAdapter, decoderAdapter, from = current - 1000, to = current)
        .collect { println("recv:${it.height}") }

    netAdapter.shutdown()
}
