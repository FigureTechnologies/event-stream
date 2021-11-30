package io.provenance.eventstream

import com.sksamuel.hoplite.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import io.provenance.eventstream.adapter.json.JSONObjectAdapter
import io.provenance.eventstream.adapter.json.decoder.Adapter
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.adapter.json.decoder.MoshiDecoderEngine
import io.provenance.eventstream.adapter.json.decoder.adapter
import io.provenance.eventstream.extensions.repeatDecodeBase64
import io.provenance.eventstream.stream.*
import io.provenance.eventstream.stream.clients.TendermintServiceOpenApiClient
import io.provenance.eventstream.stream.consumers.BlockSink
import io.provenance.eventstream.stream.consumers.blockSink
import io.provenance.eventstream.stream.infrastructure.Serializer
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.utils.sha256
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import org.slf4j.Logger
import java.io.File
import java.math.BigInteger
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
        .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
        .addSource(PropertySource.resource("/application.yml"))
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

        stream.streamBlocks()
            .buffer()
            .catch { log.error("", it) }
            .observe(observeBlock(config.verbose, logger()))
            .observe(writeJsonBlock("./data", decoderEngine))
            .collect()
    }
}

private fun <T> Flow<T>.observe(block: (T) -> Unit) = onEach { block(it) }

fun ByteArray.toHex(): String {
    val bi = BigInteger(1, this)
    return String.format("%0" + (this.size shl 1) + "X", bi)
}

@OptIn(ExperimentalStdlibApi::class)
fun writeJsonBlock(dir: String, decoderEngine: DecoderEngine): BlockSink {
    val adapter: Adapter<StreamBlock> = decoderEngine.adapter()
    File(dir).mkdirs()
    return blockSink {
        val checksum = sha256(it.height.toString()).toHex()
        val splay = checksum.take(4)
        val dirname = "$dir/$splay"

        File(dirname).let { f -> if (!f.exists()) f.mkdirs() }

        val filename = "$dirname/${it.height.toString().padStart(10, '0')}.json"
        val file = File(filename)
        if (!file.exists()) {
            file.writeText(adapter.toJson(it))
        }
    }
}

fun observeBlock(verbose: Boolean, log: Logger) = blockSink {
    val text = "Block: ${it.block.header?.height ?: "--"}:${it.block.header?.dateTime()?.toLocalDate()} ${it.block.header?.lastBlockId?.hash}" +
            "; ${it.txEvents.size} tx event(s)"
    log.info { text }

    if (verbose) {
        for (event in it.blockEvents) {
            log.info { "  Block-Event: ${event.eventType}" }
            for (attr in event.attributes) {
                log.info { "    ${attr.key?.repeatDecodeBase64()}: ${attr.value?.repeatDecodeBase64()}" }
            }
        }
        for (event in it.txEvents) {
            log.info { "  Tx-Event: ${event.eventType}" }
            for (attr in event.attributes) {
                log.info { "    ${attr.key?.repeatDecodeBase64()}: ${attr.value?.repeatDecodeBase64()}" }
            }
        }
    }
}
