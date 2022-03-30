package io.provenance.eventstream

import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.extensions.awaitShutdown
import io.provenance.eventstream.net.defaultOkHttpClient
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.nodeEventStream
import io.provenance.eventstream.stream.rpc.response.MessageType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val okHttp = defaultOkHttpClient()
    val netAdapter = okHttpNetAdapter("ws://rpc.test.provenance.io:26657", okHttp)
    val decoderAdapter = moshiDecoderAdapter()

    nodeEventStream<MessageType.NewBlockHeader>(netAdapter, decoderAdapter)
        .collect { println("newBlockHeader:\n$it") }

    okHttp.awaitShutdown()
}
