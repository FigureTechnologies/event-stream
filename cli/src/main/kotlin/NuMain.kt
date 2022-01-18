package io.provenance.eventstream

import io.confluent.kafka.serializers.KafkaJsonSerializer
import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.eventstream.config.BatchConfig
import io.provenance.eventstream.config.Config
import io.provenance.eventstream.config.EventStreamConfig
import io.provenance.eventstream.config.RpcStreamConfig
import io.provenance.eventstream.config.StreamEventsFilterConfig
import io.provenance.eventstream.config.WebsocketStreamConfig
import io.provenance.eventstream.stream.*
import io.provenance.eventstream.stream.infrastructure.Serializer.moshi
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.observers.consoleOutput
import io.provenance.eventstream.stream.observers.fileOutput
import kafka.kafkaWriter
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

@OptIn(FlowPreview::class, ExperimentalTime::class)
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
    val fromHeight by parser.option(
        ArgType.String,
        fullName = "from",
        description = "Fetch blocks starting from height, inclusive."
    )
    val toHeight by parser.option(ArgType.String, fullName = "to", description = "Fetch blocks up to height, inclusive")
    val verbose by parser.option(
        ArgType.Boolean,
        fullName = "verbose",
        shortName = "v",
        description = "Enables verbose output"
    ).default(false)
    val node by parser.option(
        ArgType.String,
        fullName = "node",
        shortName = "n",
        description = "Node to connect to for block stream"
    ).default("localhost:26657")
    val ordered by parser.option(
        ArgType.Boolean,
        fullName = "ordered",
        shortName = "o",
        description = "Order incoming blocks"
    ).default(false)
    val batchSize by parser.option(ArgType.Int, fullName = "batch", shortName = "s", description = "Batch fetch size")
        .default(16)
    val throttle by parser.option(
        ArgType.Int,
        fullName = "throttle",
        shortName = "w",
        description = "Websocket throttle duration (milliseconds)"
    ).default(0)
    val timeout by parser.option(
        ArgType.Int,
        fullName = "timeout",
        shortName = "x",
        description = "History fetch timeout (milliseconds)"
    ).default(30000)
    val concurrency by parser.option(
        ArgType.Int,
        fullName = "concurrency",
        shortName = "c",
        description = "Concurrency limit for parallel fetches"
    ).default(DEFAULT_CONCURRENCY)
    val txFilter by parser.option(
        ArgType.String,
        fullName = "filter-tx",
        shortName = "t",
        description = "Filter by tx events (comma separated)"
    ).default("")
    val blockFilter by parser.option(
        ArgType.String,
        fullName = "filter-block",
        shortName = "b",
        description = "Filter by block events (comma separated)"
    ).default("")
    parser.parse(args)

    val config = Config(
        eventStream = EventStreamConfig(
            websocket = WebsocketStreamConfig("ws://$node"),
            rpc = RpcStreamConfig("http://$node"),
            filter = StreamEventsFilterConfig(
                txEvents = txFilter.split(",").toSet(),
                blockEvents = blockFilter.split(",").toSet()
            ),
            batch = BatchConfig(batchSize, timeoutMillis = timeout.toLong())
        ),
    )

    val log = KotlinLogging.logger {}
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

    val options = EventStream.Options(
        batchSize = batchSize,
        fromHeight = fromHeight?.toLong(),
        toHeight = toHeight?.toLong(),
        skipIfEmpty = false,
        txEventPredicate = { it in config.eventStream.filter.txEvents },
        blockEventPredicate = { it in config.eventStream.filter.blockEvents },
        concurrency = concurrency,
    )

    val okClient = defaultOkHttpClient()
    val stream = defaultEventStream(config, options, okClient)

    System.setProperty("kotlinx.coroutines.debug", "on")

    // Kafka Setup
    val properties = Properties()
    properties["bootstrap.servers"] = "127.0.0.1:9092"
    properties[ProducerConfig.ACKS_CONFIG] = "all"
    properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
    properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaJsonSerializer::class.qualifiedName
    val kafkaProducer = KafkaProducer<String, StreamBlock>(properties)
    createTopic("test", 1, 3, properties)

//    kafkaProducer.use { producer ->
//        producer.send(ProducerRecord("test", "eventTesting", StreamBlock(Block(), mutableListOf<BlockEvent>(), mutableListOf()))) { m: RecordMetadata, e: Exception? ->
//            when (e) {
//                null -> log.info("Produced record to topic ${m.topic()} partition [${m.partition()}] @ offset ${m.offset()}")
//                else -> e.printStackTrace()
//            }
//        }
//        producer.flush()
//    }
    runBlocking {
        log.info("config: $config")

        stream.streamBlocks()
            .flowOn(Dispatchers.IO)
            .buffer()
            .catch { log.error("", it) }
            .onCompletion { log.info("stream fetch complete", it) }
            .onEach(fileOutput("../pio-testnet-1/json-data", moshi))
            .onEach(kafkaWriter(kafkaProducer, "test", "eventTesting"))
            .collectTo(consoleOutput(verbose, 1))
    }

    // OkHttp leaves a non-daemon executor running in the background. Have to explicitly shut down
    // the pool in order for the app to gracefully exit.
    okClient.dispatcher.executorService.shutdownNow()
    okClient.dispatcher.executorService.awaitTermination(3, TimeUnit.SECONDS)
}

fun createTopic(
    topic: String,
    partitions: Int,
    replication: Short,
    cloudConfig: Properties
) {
    val newTopic = NewTopic(topic, partitions, replication)


    try {
        with(AdminClient.create(cloudConfig)) {
            createTopics(listOf(newTopic)).all().get()
        }
    } catch (e: ExecutionException) {
        if (e.cause !is TopicExistsException) throw e
    }
}

private suspend fun Flow<StreamBlock>.collectTo(sink: BlockSink) =
    collect { sink(it) }

private fun Flow<StreamBlock>.onEach(block: BlockSink) =
    onEach { block(it) }
