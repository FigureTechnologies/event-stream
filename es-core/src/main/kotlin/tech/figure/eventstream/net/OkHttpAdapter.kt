package tech.figure.eventstream.net

import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import mu.KotlinLogging
import okhttp3.OkHttpClient
import tech.figure.eventstream.awaitShutdown
import tech.figure.eventstream.stream.clients.TendermintBlockFetcher
import tech.figure.eventstream.stream.clients.TendermintServiceOpenApiClient
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val SSL_SCHEMES = setOf("grpcs", "https", "tcp+tls", "wss")
private val NON_SSL_SCHEMES = setOf("grpc", "http", "tcp", "ws")

/**
 * Return [OkHttpClient.Builder] extension lambda that constructs the default [OkHttpClient] to use within the event stream.
 */
fun defaultOkHttpClientBuilderFn(
    pingInterval: Duration = 10.seconds,
    readInterval: Duration = 60.seconds
): OkHttpClient.Builder.() -> OkHttpClient.Builder =
    {
        pingInterval(pingInterval.toJavaDuration())
        readTimeout(readInterval.toJavaDuration())
        connectTimeout(90.seconds.toJavaDuration())
        callTimeout(30.seconds.toJavaDuration())
    }

/**
 * Create the [OkHttpClient] flavor of the required [NetAdapter] fields.
 *
 * @param node The node host address to connect to.
 * @param okHttpClient The [OkHttpClient] instance to use for http calls.
 * @return The [NetAdapter] instance.
 */
@Deprecated("Deprecated in favor of passing OkHttpClient.Builder lambda instead of OkHttpClient so client configuration cab be applied to both ws and rpc interfaces")
fun okHttpNetAdapter(node: String, okHttpClient: OkHttpClient): NetAdapter {
    val log = KotlinLogging.logger {}
    val (rpcUri, wsUri) = nodeToNetAdapterURIs(node)

    log.warn { "Using deprecated okHttpNetAdapter constructor, okHttpClient settings will only be applied to ws endpoints" }
    log.info { "initializing ws endpoint $wsUri" }
    log.info { "initializing rpc endpoint $rpcUri" }

    return netAdapter(
        okHttpClient.newWebSocketFactory("$wsUri/websocket")::create,
        TendermintBlockFetcher(TendermintServiceOpenApiClient(rpcUri)),
        okHttpClient::awaitShutdown
    )
}

fun okHttpNetAdapter(
    node: String,
    clientBuilderFn: OkHttpClient.Builder.() -> OkHttpClient.Builder = defaultOkHttpClientBuilderFn()
): NetAdapter {
    val log = KotlinLogging.logger {}
    val (rpcUri, wsUri) = nodeToNetAdapterURIs(node)

    log.info { "initializing ws endpoint $wsUri" }
    log.info { "initializing rpc endpoint $rpcUri" }

    val okHttpClient = clientBuilderFn(OkHttpClient.Builder()).build()

    return netAdapter(
        okHttpClient.newWebSocketFactory("$wsUri/websocket")::create,
        TendermintBlockFetcher(TendermintServiceOpenApiClient(rpcUri, clientBuilderFn)),
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
