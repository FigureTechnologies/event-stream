package io.provenance.eventstream

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.EnvironmentVariablesPropertySource
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.preprocessor.PropsPreprocessor
import com.tinder.scarlet.MessageAdapter
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.adapter.json.decoder.MoshiDecoderEngine
import io.provenance.eventstream.config.Config
import io.provenance.eventstream.config.Environment
import io.provenance.eventstream.decoder.defaultMoshi
import io.provenance.eventstream.net.NetAdapter
import io.provenance.eventstream.net.defaultOkHttpClient
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.BlockStreamOptions
import io.provenance.eventstream.stream.DefaultBlockStreamFactory
import io.provenance.eventstream.stream.TendermintServiceClient
import io.provenance.eventstream.stream.WebSocketChannel
import io.provenance.eventstream.stream.clients.TendermintBlockFetcher
import io.provenance.eventstream.stream.clients.TendermintServiceOpenApiClient
import io.provenance.eventstream.stream.flows.DEFAULT_THROTTLE_PERIOD
import io.provenance.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import java.lang.reflect.Type
import java.net.URI
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Create the default [TendermintServiceClient] to use with the event stream.
 *
 * @param rpcUri The URI of the Tendermint RPC service to connect to.
 * @return The [TendermintServiceClient] instance to use for the event stream.
 */
fun defaultTendermintService(rpcUri: String): TendermintServiceClient =
    TendermintServiceOpenApiClient(URI(rpcUri))

/**
 * Adapter type wrapper for [WebSocket.Factory] lambdas.
 */
typealias WsAdapter = () -> WebSocket

/**
 * Adapter method to convert lambda into [WebSocket.Factory]
 */
fun WsAdapter.toScarletFactory(): WebSocket.Factory {
    return object : WebSocket.Factory {
        override fun create(): WebSocket = invoke()
    }
}

/**
 * Adapter type wrapper for [MessageAdapter.Factory] lambdas.
 */
typealias WsDecoderAdapter = (type: Type, annotations: Array<Annotation>) -> MessageAdapter<*>

/**
 * Adapter method to convert lambda into [MessageAdapter.Factory]
 */
fun WsDecoderAdapter.toScarletFactory(): MessageAdapter.Factory {
    return object : MessageAdapter.Factory {
        override fun create(type: Type, annotations: Array<Annotation>): MessageAdapter<*> = invoke(type, annotations)
    }
}

/**
 * Create a [WebSocketChannel] instance via [Scarlet].
 *
 * @param wsFactory The [WsAdapter] used to create [WebSocket] used within [Scarlet] to connect to a host.
 * @param msgAdapterFactory The [WsDecoderAdapter] to use within the web socket to convert into [com.tinder.scarlet.Message].
 * @param throttle The rate to throttle within [Scarlet] for interval polling.
 * @param lifecycle The [LifecycleRegistry] to use for websocket spin-up and tear-down.
 * @return The [WebSocketChannel] instance.
 */
fun defaultWebSocketChannel(
    wsFactory: WsAdapter,
    msgAdapterFactory: WsDecoderAdapter,
    throttle: Duration = DEFAULT_THROTTLE_PERIOD,
    lifecycle: LifecycleRegistry = defaultLifecycle(throttle),
): WebSocketChannel {
    val scarlet = Scarlet.Builder()
        .webSocketFactory(wsFactory.toScarletFactory())
        .addMessageAdapterFactory(msgAdapterFactory.toScarletFactory())
        .addStreamAdapterFactory(CoroutinesStreamAdapterFactory())
        .lifecycle(lifecycle)
        .build()
    return scarlet.create(WebSocketChannel::class.java)
}

/**
 * Create a [LifecycleRegistry] used to manage websocket spin-up and tear-down within [Scarlet].
 */
fun defaultLifecycle(throttle: Duration = DEFAULT_THROTTLE_PERIOD): LifecycleRegistry =
    LifecycleRegistry(throttle.inWholeMilliseconds)

/**
 * ---------------------------------------------------------------------------
 * Old stuff - TODO work to remove this
 * ---------------------------------------------------------------------------
 */

/**
 *
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
fun defaultEventStream(
    config: Config,
    options: BlockStreamOptions,
    okHttpClient: OkHttpClient = defaultOkHttpClient(),
    decoderEngine: DecoderEngine = MoshiDecoderEngine(defaultMoshi()),
    fetcher: TendermintBlockFetcher = defaultTendermintFetcher(config.eventStream.rpc.uri)
): BlockSource<StreamBlockImpl> {
    val factory = DefaultBlockStreamFactory(
        config = config,
        decoderEngine = decoderEngine,
        eventStreamBuilder = defaultEventStreamBuilder(okHttpNetAdapter(config.eventStream.websocket.uri, okHttpClient)),
        blockFetcher = fetcher
    )
    return factory.createSource(options)
}

/**
 * Create the default [TendermintServiceClient] to use with the event stream.
 *
 * @param rpcUri The URI of the Tendermint RPC service to connect to.
 * @return The [TendermintServiceClient] instance to use for the event stream.
 */
fun defaultTendermintFetcher(rpcUri: String): TendermintBlockFetcher =
    TendermintBlockFetcher(TendermintServiceOpenApiClient(URI(rpcUri)))

/**
 * Create a default websocket builder for the event stream.
 */
@OptIn(ExperimentalTime::class)
fun defaultEventStreamBuilder(netAdapter: NetAdapter): Scarlet.Builder {
    val factory = object : WebSocket.Factory {
        override fun create(): WebSocket = netAdapter.wsAdapter.invoke()
    }
    return Scarlet.Builder()
        .webSocketFactory(factory)
        .addMessageAdapterFactory(MoshiMessageAdapter.Factory())
        .addStreamAdapterFactory(CoroutinesStreamAdapterFactory())
}

/**
 * Create a default configuration to use for the event stream.
 *
 * @param environment The environment to generate a configutation with.
 * @return The configuration
 */
fun defaultConfig(environment: Environment): Config = ConfigLoader.Builder()
    .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
    .apply {
        // If in the local environment, override the ${...} envvar values in `application.properties` with
        // the values provided in the local-specific `local.env.properties` property file:
        if (environment.isLocal()) {
            addPreprocessor(PropsPreprocessor("/local.env.properties"))
        }
    }
    .addSource(PropertySource.resource("/application.yml"))
    .build()
    .loadConfigOrThrow()
