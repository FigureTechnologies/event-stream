package io.provenance.eventstream.net

import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import io.provenance.eventstream.WsAdapter
import io.provenance.eventstream.stream.clients.BlockFetcher
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
 * Create a default okHttpClient to use for the event stream.
 */
@OptIn(ExperimentalTime::class)
fun defaultOkHttpClient(pingInterval: Duration = 10.seconds, readInterval: Duration = 60.seconds) =
    OkHttpClient.Builder()
        .pingInterval(pingInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .readTimeout(readInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .build()

fun okHttpNetAdapter(host: String, okHttpClient: OkHttpClient = defaultOkHttpClient(), tls: Boolean = false): NetAdapter {
    fun scheme(pre: String) = if (tls) "${pre}s" else pre

    return object : NetAdapter {
        private val log = KotlinLogging.logger {}

        val wsUri = URI.create("${scheme("ws")}://$host")
        val rpcUri = URI.create("${scheme("http")}://$host")

        init {
            log.info { "initializing ws endpoint:$wsUri" }
            log.info { "initializing rpc endpoint:$rpcUri" }
        }

        override val wsAdapter: WsAdapter =
            okHttpClient.newWebSocketFactory("${wsUri.scheme}://${wsUri.host}:${wsUri.port}/websocket")::create
        override val rpcAdapter: BlockFetcher =
            TendermintBlockFetcher(TendermintServiceOpenApiClient(rpcUri.toASCIIString()))
    }
}
