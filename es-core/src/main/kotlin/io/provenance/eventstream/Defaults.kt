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
import io.provenance.eventstream.net.defaultOkHttpClient
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.BlockStreamOptions
import io.provenance.eventstream.stream.DEFAULT_THROTTLE_PERIOD
import io.provenance.eventstream.stream.DefaultBlockStreamFactory
import io.provenance.eventstream.stream.TendermintServiceClient
import io.provenance.eventstream.stream.WebSocketChannel
import io.provenance.eventstream.stream.clients.TendermintBlockFetcher
import io.provenance.eventstream.stream.clients.TendermintServiceOpenApiClient
import io.provenance.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import java.lang.reflect.Type
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

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

/**
 * Create a default websocket builder for the event stream.
 */
@OptIn(ExperimentalTime::class)
fun defaultEventStreamBuilder(wsFactory: () -> WebSocket): Scarlet.Builder {
    val factory = object : WebSocket.Factory {
        override fun create(): WebSocket = wsFactory()
    }
    return Scarlet.Builder()
        .webSocketFactory(factory)
        .addMessageAdapterFactory(MoshiMessageAdapter.Factory())
        .addStreamAdapterFactory(CoroutinesStreamAdapterFactory())
}

/**
 * Create the default [TendermintServiceClient] to use with the event stream.
 *
 * @param rpcUri The URI of the Tendermint RPC service to connect to.
 * @return The [TendermintServiceClient] instance to use for the event stream.
 */
fun defaultTendermintService(rpcUri: String): TendermintServiceClient =
    TendermintServiceOpenApiClient(rpcUri)

/**
 * Create the default [TendermintServiceClient] to use with the event stream.
 *
 * @param rpcUri The URI of the Tendermint RPC service to connect to.
 * @return The [TendermintServiceClient] instance to use for the event stream.
 */
fun defaultTendermintFetcher(rpcUri: String): TendermintBlockFetcher =
    TendermintBlockFetcher(TendermintServiceOpenApiClient(rpcUri))

/**
 *
 */
typealias WsAdapter = () -> WebSocket

/**
 *
 */
fun WsAdapter.toScarletFactory(): WebSocket.Factory {
    return object : WebSocket.Factory {
        override fun create(): WebSocket = invoke()
    }
}

/**
 *
 */
typealias WsDecoderAdapter = (type: Type, annotations: Array<Annotation>) -> MessageAdapter<*>

/**
 *
 */
fun WsDecoderAdapter.toScarletFactory(): MessageAdapter.Factory {
    return object : MessageAdapter.Factory {
        override fun create(type: Type, annotations: Array<Annotation>): MessageAdapter<*> = invoke(type, annotations)
    }
}

/**
 *
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
 *
 */
fun defaultLifecycle(throttle: Duration = DEFAULT_THROTTLE_PERIOD): LifecycleRegistry =
    LifecycleRegistry(throttle.inWholeMilliseconds)

/**
 * Old stuff - TODO remove this.
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
