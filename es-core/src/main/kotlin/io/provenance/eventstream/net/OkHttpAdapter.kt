package io.provenance.eventstream.net

import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import io.provenance.eventstream.extensions.awaitShutdown
import io.provenance.eventstream.stream.clients.TendermintBlockFetcher
import io.provenance.eventstream.stream.clients.TendermintServiceOpenApiClient
import mu.KotlinLogging
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Create a default [OkHttpClient] to use within the event stream.
 */
@OptIn(ExperimentalTime::class)
fun defaultOkHttpClient(pingInterval: Duration = 10.seconds, readInterval: Duration = 60.seconds) =
    OkHttpClient.Builder()
        .pingInterval(pingInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .readTimeout(readInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .build()

/**
 * Create the [OkHttpClient] flavor of the required [NetAdapter] fields.
 *
 * @param hosh The node host address to connect to.
 * @param okHttpClient The [OkHttpClient] instance to use for http calls.
 * @param tls Are the connections running over tls?
 * @return The [NetAdapter] instance.
 */
fun okHttpNetAdapter(host: String, okHttpClient: OkHttpClient = defaultOkHttpClient(), tls: Boolean = false): NetAdapter {
    fun scheme(pre: String) = if (tls) "${pre}s" else pre

    val log = KotlinLogging.logger {}
    val wsUri = URI.create("${scheme("ws")}://$host")
    val rpcUri = URI.create("${scheme("http")}://$host")

    log.info { "initializing ws endpoint:$wsUri" }
    log.info { "initializing rpc endpoint:$rpcUri" }

    return netAdapter(
        okHttpClient.newWebSocketFactory("${wsUri.scheme}://${wsUri.host}:${wsUri.port}/websocket")::create,
        TendermintBlockFetcher(TendermintServiceOpenApiClient(URI(rpcUri.toASCIIString()))),
        okHttpClient::awaitShutdown
    )
}
