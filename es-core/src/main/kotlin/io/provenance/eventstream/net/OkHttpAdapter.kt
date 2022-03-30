package io.provenance.eventstream.net

import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import io.provenance.eventstream.WsAdapter
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Create a default okHttpClient to use for the event stream.
 */
@OptIn(ExperimentalTime::class)
fun defaultOkHttpClient(pingInterval: Duration = Duration.seconds(10), readInterval: Duration = Duration.seconds(60)) =
    OkHttpClient.Builder()
        .pingInterval(pingInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .readTimeout(readInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .build()

fun okHttpNetAdapter(uri: String, okHttpClient: OkHttpClient = defaultOkHttpClient()): WsAdapter {
    val node = URI.create(uri)
    return okHttpClient.newWebSocketFactory("${node.scheme}://${node.host}:${node.port}/websocket")::create
}
