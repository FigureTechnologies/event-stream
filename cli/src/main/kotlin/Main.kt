package io.provenance.eventstream

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.EnvironmentVariablesPropertySource
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.preprocessor.PropsPreprocessor
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
import io.provenance.eventstream.stream.consumers.EventStreamViewer
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.extensions.dateTime
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
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
    val parser = ArgParser("provenance-event-stream")
    val envFlag by parser.option(
        ArgType.Choice<Environment>(),
        shortName = "e",
        fullName = "env",
        description = "Specify the application environment. If not present, fall back to the `\$ENVIRONMENT` envvar",
    )
    val fromHeight by parser.option(
        ArgType.Int,
        fullName = "from",
        description = "Fetch blocks starting from height, inclusive."
    )
    val toHeight by parser.option(
        ArgType.Int, fullName = "to", description = "Fetch blocks up to height, inclusive"
    )
    val observe by parser.option(
        ArgType.Boolean, fullName = "observe", description = "Observe blocks instead of upload"
    ).default(true)
    val restart by parser.option(
        ArgType.Boolean,
        fullName = "restart",
        description = "Restart processing blocks from the last maximum historical block height recorded"
    ).default(false)
    val verbose by parser.option(
        ArgType.Boolean, shortName = "v", fullName = "verbose", description = "Enables verbose output"
    ).default(false)
    val skipIfEmpty by parser.option(
        ArgType.Choice(listOf(false, true), { it.toBooleanStrict() }),
        fullName = "skip-if-empty",
        description = "Skip blocks that have no transactions"
    ).default(true)

    parser.parse(args)

    val environment: Environment =
        envFlag ?: runCatching { Environment.valueOf(System.getenv("ENVIRONMENT")) }
            .getOrElse {
                error("Not a valid environment: ${System.getenv("ENVIRONMENT")}")
            }

    val config: Config = ConfigLoader.Builder()
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

    val log = "main".logger()

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(JSONObjectAdapter())
        .build()
    val wsStreamBuilder = configureEventStreamBuilder(config.eventStream.websocket.uri)
    val tendermintService = TendermintServiceOpenApiClient(config.eventStream.rpc.uri)

    runBlocking(Dispatchers.IO) {

        log.info(
            """
            |run options => {
            |    restart = $restart
            |    from-height = $fromHeight 
            |    to-height = $toHeight
            |    skip-if-empty = $skipIfEmpty
            |}
            """.trimMargin("|")
        )

        val options = EventStream.Options
            .builder()
            .batchSize(config.eventStream.batch.size)
            .fromHeight(fromHeight?.toLong())
            .toHeight(toHeight?.toLong())
            .skipIfEmpty(skipIfEmpty)
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

        if (observe) {
            log.info("*** Observing blocks and events. No action will be taken. ***")
            EventStreamViewer(
                EventStream.Factory(config, moshi, wsStreamBuilder, tendermintService),
                options
            )
                .consume { b: StreamBlock ->
                    val text = "Block: ${b.block.header?.height ?: "--"}:${b.block.header?.dateTime()?.toLocalDate()} ${b.block.header?.lastBlockId?.hash}" +
                            "; ${b.txEvents.size} tx event(s)"
                    println(
                        if (b.historical) {
                            text
                        } else {
                            green(text)
                        }
                    )
                    if (verbose) {
                        for (event in b.blockEvents) {
                            println("  Block-Event: ${event.eventType}")
                            for (attr in event.attributes) {
                                println("    ${attr.key?.repeatDecodeBase64()}: ${attr.value?.repeatDecodeBase64()}")
                            }
                        }
                        for (event in b.txEvents) {
                            println("  Tx-Event: ${event.eventType}")
                            for (attr in event.attributes) {
                                println("    ${attr.key?.repeatDecodeBase64()}: ${attr.value?.repeatDecodeBase64()}")
                            }
                        }
                    }
                    println()
                }
        }
    }
}
