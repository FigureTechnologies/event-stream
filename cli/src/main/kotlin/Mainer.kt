package io.provenance.eventstream

import io.provenance.eventstream.config.BatchConfig
import io.provenance.eventstream.config.Config
import io.provenance.eventstream.config.EventStreamConfig
import io.provenance.eventstream.stream.*
import io.provenance.eventstream.stream.consumers.BlockSink
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.observers.consoleOutput
import io.provenance.eventstream.stream.observers.fileOutput
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

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
    val fromHeight by parser.option(ArgType.String, fullName = "from", description = "Fetch blocks starting from height, inclusive.")
    val toHeight by parser.option(ArgType.String, fullName = "to", description = "Fetch blocks up to height, inclusive")
    val verbose by parser.option(ArgType.Boolean, fullName = "verbose", shortName = "v", description = "Enables verbose output").default(false)
    val node by parser.option(ArgType.String, fullName = "node", shortName = "n", description = "Node to connect to for block stream").default("localhost:26657")
    val ordered by parser.option(ArgType.Boolean, fullName = "ordered", shortName = "o", description = "Order incoming blocks").default(false)
    val batchSize by parser.option(ArgType.Int, fullName = "batch", shortName = "s", description = "Batch fetch size").default(16)
    val throttle by parser.option(ArgType.Int, fullName = "throttle", shortName = "w", description = "Websocket throttle duration (milliseconds)").default(0)
    val timeout by parser.option(ArgType.Int, fullName = "timeout", shortName = "x", description = "History fetch timeout (milliseconds)").default(30000)

    parser.parse(args)

    val config = Config(
        node = node,
        from = fromHeight?.toLong(),
        to = toHeight?.toLong(),
        ordered = ordered,
        wsThrottleDurationMs = throttle.toLong(),
        eventStream = EventStreamConfig(BatchConfig(batchSize, timeout.toLong()))
    )

    val log = KotlinLogging.logger {}
    val decoderEngine = defaultDecoderEngine()
    val okClient = defaultOkHttpClient()
    val factory: BlockStreamFactory = defaultBlockStreamFactory(config, okClient)
    val stream: BlockSource = factory.fromConfig(config)

    runBlocking {
        log.info("config: $config")

        stream.streamBlocks(config.from, config.to)
            .flowOn(Dispatchers.IO)
            .buffer()
            .catch { log.error("", it) }
            .observe(consoleOutput(verbose))
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