package io.provenance.eventstream

import com.sksamuel.hoplite.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import com.tinder.scarlet.websocket.okhttp.newWebSocketFactory
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import io.provenance.eventstream.adapter.json.JSONObjectAdapter
import io.provenance.eventstream.extensions.repeatDecodeBase64
import io.provenance.eventstream.stream.EventStream
import io.provenance.eventstream.stream.clients.TendermintServiceOpenApiClient
import io.provenance.eventstream.stream.consumers.BlockSink
//import io.provenance.eventstream.stream.consumers.EventStreamViewer
import io.provenance.eventstream.stream.consumers.blockSink
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.extensions.dateTime
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
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
    val parser = ArgParser("provenance-event-stream", skipExtraArguments = true)
    val fromHeight by parser.option(
        ArgType.Int,
        fullName = "from",
        description = "Fetch blocks starting from height, inclusive."
    )
    val toHeight by parser.option(
        ArgType.Int, fullName = "to", description = "Fetch blocks up to height, inclusive"
    )
    val verbose by parser.option(
        ArgType.Boolean, shortName = "v", fullName = "verbose", description = "Enables verbose output"
    ).default(false)

    parser.parse(args)

    val config: Config = ConfigLoader.Builder()
        .addCommandLineSource(args)
        .addSource(EnvironmentVariablesPropertySource(useUnderscoresAsSeparator = true, allowUppercaseNames = true))
        .addSource(PropertySource.resource("/application.yml"))
        .build()
        .loadConfigOrThrow()

    val outputs = Outputs.outputs()

    val log = "main".logger()

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .add(JSONObjectAdapter())
        .build()
    val wsStreamBuilder = configureEventStreamBuilder(config.eventStream.websocket.uri)
    val tendermintService = TendermintServiceOpenApiClient(config.eventStream.rpc.uri)

    val options = EventStream.Options
        .builder()
        .batchSize(config.eventStream.batch.size)
        .fromHeight(fromHeight?.toLong())
        .toHeight(toHeight?.toLong())
        .apply {
            if (config.eventStream.filter.txEvents.isNotEmpty()) {
                matchTxEvent { it in config.eventStream.filter.txEvents }
            }
        }
        .apply {
            if (config.eventStream.filter.blockEvents.isNotEmpty()) {
                matchBlockEvent { it in config.eventStream.filter.blockEvents }
            }
        }
        .also {
            if (config.eventStream.filter.txEvents.isNotEmpty()) {
                log.info("Listening for tx events:")
                for (event in config.eventStream.filter.txEvents) {
                    log.info(" - $event")
                }
            }
            if (config.eventStream.filter.blockEvents.isNotEmpty()) {
                log.info("Listening for block events:")
                for (event in config.eventStream.filter.blockEvents) {
                    log.info(" - $event")
                }
            }
        }
        .build()

    val stream = EventStream.Factory(config, moshi, wsStreamBuilder, tendermintService).create(options)

    runBlocking(Dispatchers.IO) {
        log.info("opts:$options")

        stream.streamBlocks()
            .buffer()
            .catch { log.error("", it) }
            .observe(observeBlock(verbose))
            .observe(writeJsonBlock("./data"))
            .collect()
    }
}

private fun <T> Flow<T>.observe(block: (T) -> Unit) = onEach { block(it) }

fun writeJsonBlock(dir: String): BlockSink {
    val d = File(dir)
    if (!d.exists()) {
        d.mkdirs()
    }

    return blockSink {
        val filename = "$dir/${it.height}.json"
        val file = File(filename)
        if (!file.exists()) {
            file.writeText(org.json.JSONWriter.valueToString(it))
            println("wrote block data to $filename")
        } else {
            println("skipping block data for $filename: exists")
            // file exists
        }
    }
}

fun observeBlock(verbose: Boolean) = blockSink {
    val text = "Block: ${it.block.header?.height ?: "--"}:${it.block.header?.dateTime()?.toLocalDate()} ${it.block.header?.lastBlockId?.hash}" +
            "; ${it.txEvents.size} tx event(s)"
    println(if (it.historical) text else green(text))

    if (verbose) {
        for (event in it.blockEvents) {
            println("  Block-Event: ${event.eventType}")
            for (attr in event.attributes) {
                println("    ${attr.key?.repeatDecodeBase64()}: ${attr.value?.repeatDecodeBase64()}")
            }
        }
        for (event in it.txEvents) {
            println("  Tx-Event: ${event.eventType}")
            for (attr in event.attributes) {
                println("    ${attr.key?.repeatDecodeBase64()}: ${attr.value?.repeatDecodeBase64()}")
            }
        }
    }
    println()
}
