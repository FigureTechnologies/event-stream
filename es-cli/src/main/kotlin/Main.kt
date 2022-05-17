package io.provenance.eventstream

// linting is giving a hard time on these kotlinx packages that cannot be auto-corrected,
// IntelliJ is also giving linting errors if the imports are changed to individual imports.
// ktlint-disable no-wildcard-imports
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.addCommandLineSource
import com.sksamuel.hoplite.sources.EnvironmentVariablesPropertySource
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.adapter.json.JSONObjectAdapter
import io.provenance.eventstream.adapter.json.decoder.MoshiDecoderEngine
import io.provenance.eventstream.config.Config
import io.provenance.eventstream.stream.BlockStreamFactory
import io.provenance.eventstream.stream.DefaultBlockStreamFactory
import io.provenance.eventstream.stream.clients.TendermintBlockFetcher
import io.provenance.eventstream.stream.clients.TendermintServiceOpenApiClient
import io.provenance.eventstream.stream.infrastructure.Serializer
import io.provenance.eventstream.stream.infrastructure.Serializer.moshi
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.models.StreamBlockImplJsonAdapter
import io.provenance.eventstream.stream.observers.consoleOutput
import io.provenance.eventstream.stream.observers.fileOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

private fun configureEventStreamBuilder(client: OkHttpClient, node: String = "localhost:26657", uri: URI = URI("$node")): Scarlet.Builder {
    return Scarlet.Builder()
        .webSocketFactory(client.newWebSocketFactory("${uri.scheme}://${uri.host}:${uri.port}/websocket"))
        .addMessageAdapterFactory(MoshiMessageAdapter.Factory())
        .addStreamAdapterFactory(CoroutinesStreamAdapterFactory())
}

@OptIn(FlowPreview::class, kotlin.time.ExperimentalTime::class)
@ExperimentalCoroutinesApi
fun main(args: Array<String>) {
    /**
     * All configuration options can be overridden via environment variables:
     *
     * - To override nested configuration options separated with a dot ("."), use double underscores ("__")
     *   in the environment variable:
     *     event.stream.rpc_uri=http://localhost:26657 is overridden by "event__stream_rpc_uri=foo"
     *
     * @see https://github.com/sksamuel/hoplite#environmentvariablespropertysource
     */

    val config: Config = ConfigLoaderBuilder.default()
        .addCommandLineSource(args)
        .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
        .addSource(PropertySource.resource("/application.yml"))
        .build()
        .loadConfigOrThrow()

    val log = KotlinLogging.logger {}

    val decoderEngine = Serializer.moshiBuilder
        .addLast(KotlinJsonAdapterFactory())
        .add(JSONObjectAdapter())
        .add(StreamBlock::class.java, StreamBlockImplJsonAdapter(moshi))
        .build()
        .let { MoshiDecoderEngine(it) }

    val okClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val wsStreamBuilder = configureEventStreamBuilder(okClient, config.eventStream.websocket.uri)
    val tendermintServiceClient = TendermintServiceOpenApiClient(config.node)
    val tendermintService = TendermintBlockFetcher(tendermintServiceClient)

//    val dispatcher = object : DispatcherProvider {
//        private val single = Executors.newSingleThreadExecutor {
//            Thread(it).also { t -> t.isDaemon = true }
//        }.asCoroutineDispatcher()
//        override fun io(): CoroutineDispatcher = single
//        override fun default(): CoroutineDispatcher = single
//        override fun main(): CoroutineDispatcher = single
//        override fun unconfined(): CoroutineDispatcher = single
//    }

    val factory: BlockStreamFactory = DefaultBlockStreamFactory(config, decoderEngine, wsStreamBuilder, tendermintService)
    val stream: BlockSource<StreamBlockImpl> = factory.fromConfig(config)

    runBlocking {
        log.info("config: $config")

        stream.streamBlocks()
            .flowOn(Dispatchers.IO)
            .buffer()
            .catch { log.error("", it) }
            .observe(consoleOutput(config.verbose))
            .observe(fileOutput("../pio-testnet-1/json-data", decoderEngine))
            .onCompletion { log.info("stream fetch complete", it) }
            .collect()
    }

    // OkHttp leaves a non-daemon executor running in the background. Have to explicitly shut down
    // the pool in order for the app to gracefully exit.
    okClient.dispatcher.executorService.shutdown()
    okClient.dispatcher.executorService.awaitTermination(1, TimeUnit.SECONDS)
}

private fun Flow<StreamBlock>.observe(block: BlockSink) = onEach { block(it) }

private fun Flow<StreamBlock>.onEach(block: BlockSink) =
    onEach { block(it) }
