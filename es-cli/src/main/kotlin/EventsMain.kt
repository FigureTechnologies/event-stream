package io.provenance.eventstream

import io.provenance.eventstream.extensions.awaitShutdown
import io.provenance.eventstream.stream.decodeMessages
import io.provenance.eventstream.stream.nodeWebSocketClient
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

private suspend fun <R> OkHttpClient.use(block: suspend (OkHttpClient) -> R): R =
    block(this).also { awaitShutdown() }

fun main() = runBlocking {
    defaultOkHttpClient().use {
        nodeWebSocketClient("ws://34.148.31.174:26657", it, subscription = "tm.event='NewBlockHeader'")
            .decodeMessages().take(2)
            .collect {
                println(it)
            }
    }
}
