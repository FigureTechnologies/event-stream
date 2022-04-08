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

private val SSL_SCHEMES = setOf("grpcs", "https", "tcp+tls", "wss")
private val NON_SSL_SCHEMES = setOf("grpc", "http", "tcp", "ws")
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
 * @return The [NetAdapter] instance.
 */
fun okHttpNetAdapter(node: String, okHttpClient: OkHttpClient = defaultOkHttpClient()): NetAdapter {
    val log = KotlinLogging.logger {}
    val (rpcUri, wsUri) = nodeToNetAdapterURIs(node)

    log.info { "initializing ws endpoint $wsUri" }
    log.info { "initializing rpc endpoint $rpcUri" }

    return netAdapter(
        okHttpClient.newWebSocketFactory("$wsUri/websocket")::create,
        TendermintBlockFetcher(TendermintServiceOpenApiClient(rpcUri)),
        okHttpClient::awaitShutdown
    )
}

private fun nodeToNetAdapterURIs(node: String): Pair<String, String> {
    val parsed = URI(node).normalize()
    require(parsed.scheme in SSL_SCHEMES + NON_SSL_SCHEMES) { "invalid scheme in uri '$node'" }
    require(parsed.host != null) { "host is required in uri '$node'" }

    val scheme = if (parsed.scheme in SSL_SCHEMES) "s" else ""
    val port =
        if (parsed.port == -1) {
            if (parsed.scheme in SSL_SCHEMES) 443
            else 80
        } else parsed.port

    val base = "${parsed.host}:$port"
    return "http$scheme://$base" to "ws$scheme://$base"
}
