package io.provenance.eventstream

import com.sksamuel.hoplite.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import io.provenance.eventstream.adapter.json.JSONObjectAdapter
import io.provenance.eventstream.adapter.json.decoder.MoshiDecoderEngine
import io.provenance.eventstream.stream.*
import io.provenance.eventstream.stream.clients.TendermintServiceOpenApiClient
import io.provenance.eventstream.stream.infrastructure.Serializer
import io.provenance.eventstream.stream.observers.consoleOutput
import io.provenance.eventstream.stream.observers.fileOutput
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

private fun configureEventStreamBuilder(websocketUri: String): Scarlet.Builder {
    val node = URI(websocketUri)
    return Scarlet.Builder()
        .webSocketFactory(
            OkHttpClient.Builder()
                .pingInterval(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
                .newWebSocketFactory("${node.scheme}://${node.host}:${node.port}/websocket")
        )
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
    val config: Config = ConfigLoader.Builder()
        .addCommandLineSource(args)
        .addEnvironmentSource(useUnderscoresAsSeparator = true, allowUppercaseNames = true)
        .addPropertySource(PropertySource.resource("/application.yml"))
        .build()
        .loadConfigOrThrow()

    val log = "main".logger()

    val decoderEngine = Serializer.moshiBuilder
        .addLast(KotlinJsonAdapterFactory())
        .add(JSONObjectAdapter())
        .build()
        .let { MoshiDecoderEngine(it) }

    val wsStreamBuilder = configureEventStreamBuilder(config.eventStream.websocket.uri)
    val tendermintService = TendermintServiceOpenApiClient(config.eventStream.rpc.uri)

    val factory: BlockStreamFactory = DefaultBlockStreamFactory(config, decoderEngine, wsStreamBuilder, tendermintService)
    val stream: BlockSource = factory.fromConfig(config)

    runBlocking(Dispatchers.IO) {
        log.info("config: $config")

        stream.streamBlocks(config.eventStream.height.from, config.eventStream.height.to)
            .buffer()
            .catch { log.error("", it) }
            .observe(consoleOutput(config.verbose))
            .observe(fileOutput("../pio-testnet-1/json-data", decoderEngine))
            .collect()
    }
}

private fun <T> Flow<T>.observe(block: (T) -> Unit) = onEach { block(it) }
