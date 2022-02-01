package io.provenance.eventstream

import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.eventstream.config.BatchConfig
import io.provenance.eventstream.config.Config
import io.provenance.eventstream.config.EventStreamConfig
import io.provenance.eventstream.config.RpcStreamConfig
import io.provenance.eventstream.config.StreamEventsFilterConfig
import io.provenance.eventstream.config.WebsocketStreamConfig
import io.provenance.eventstream.flow.kafka.UnAckedConsumerRecordImpl
import io.provenance.eventstream.stream.*
import io.provenance.eventstream.stream.infrastructure.Serializer.moshi
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.observers.fileOutput
import io.provenance.eventstream.flow.kafka.acking
import kafka.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.ExperimentalTime

val commonProps = { id: String? ->
    mapOf(
        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
        CommonClientConfigs.CLIENT_ID_CONFIG to (id ?: UUID.randomUUID().toString()),
    )
}

val producerProps = mapOf(
    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaSerializer::class.java,
    ProducerConfig.ACKS_CONFIG to "all",
    ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
)

val consumerProps = mapOf(
    ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 15,
    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
    ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to 10000,
    ConsumerConfig.GROUP_ID_CONFIG to "test-group",
    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaDeserializer::class.java
)

fun logger(name: String): org.slf4j.Logger = LoggerFactory.getLogger(name)
var org.slf4j.Logger.level: Level
    get() = (this as Logger).level
    set(value) { (this as Logger).level = value }

fun producerProps(id: String) = commonProps(id) + producerProps
fun consumerProps(id: String) = commonProps(id) + consumerProps

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
    val fromHeight by parser.option(ArgType.String, fullName = "from", description = "Fetch blocks starting from height, inclusive.")
    val toHeight by parser.option(ArgType.String, fullName = "to", description = "Fetch blocks up to height, inclusive")
    val verbose by parser.option(ArgType.Boolean, fullName = "verbose", shortName = "v", description = "Enables verbose output").default(false)
    val node by parser.option(ArgType.String, fullName = "node", shortName = "n", description = "Node to connect to for block stream").default("localhost:26657")
    val ordered by parser.option(ArgType.Boolean, fullName = "ordered", shortName = "o", description = "Order incoming blocks").default(false)
    val batchSize by parser.option(ArgType.Int, fullName = "batch", shortName = "s", description = "Batch fetch size").default(16)
    val throttle by parser.option(ArgType.Int, fullName = "throttle", shortName = "w", description = "Websocket throttle duration (milliseconds)").default(0)
    val timeout by parser.option(ArgType.Int, fullName = "timeout", shortName = "x", description = "History fetch timeout (milliseconds)").default(30000)
    val concurrency by parser.option(ArgType.Int, fullName = "concurrency", shortName = "c", description = "Concurrency limit for parallel fetches").default(DEFAULT_CONCURRENCY)
    val txFilter by parser.option(ArgType.String, fullName = "filter-tx", shortName = "t", description = "Filter by tx events (comma separated)").default("")
    val blockFilter by parser.option(ArgType.String, fullName = "filter-block", shortName = "b", description = "Filter by block events (comma separated)").default("")
    val keepEmpty by parser.option(ArgType.Boolean, fullName = "keep-empty", description = "Keep empty blocks").default(true)
    parser.parse(args)

    val config = Config(
        eventStream = EventStreamConfig(
            skipEmpty = !keepEmpty,
            websocket = WebsocketStreamConfig("ws://$node"),
            rpc = RpcStreamConfig("http://$node"),
            filter = StreamEventsFilterConfig(
                txEvents = txFilter.split(",").filter { it.isNotBlank() }.toSet(),
                blockEvents = blockFilter.split(",").filter { it.isNotBlank() }.toSet()),
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
        skipIfEmpty = config.eventStream.skipEmpty,
        txEventPredicate = { config.eventStream.filter.txEvents.isEmpty() || it in config.eventStream.filter.txEvents },
        blockEventPredicate = { config.eventStream.filter.blockEvents.isEmpty() || it in config.eventStream.filter.blockEvents },
        concurrency = concurrency,
    )

    val okClient = defaultOkHttpClient()
    val stream = defaultEventStream(config, options, okClient)

    System.setProperty("kotlinx.coroutines.debug", "on")

    // Kafka Setup
//    val properties = Properties()
//    properties["application.id"] = "event-stream"
//    properties["bootstrap.servers"] = "127.0.0.1:9092"
//    properties[ProducerConfig.ACKS_CONFIG] = "all"
////    We have to specify our own serializer
//    properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.qualifiedName
//    properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = KafkaJsonSerializer::class.qualifiedName
//    properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
//    properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaJsonSerializer::class.qualifiedName
    val kafkaProducer = KafkaProducer<String, StreamBlockImpl>(producerProps("producer"))
//    val kafkaConsumer = KafkaConsumer<String, BaseStreamBlock>(consumerProps("consumer"))
////    createTopic("test", 1, 3, properties)


    runBlocking {
        log.info("config: $config")

        val chainStreamFlow = stream.streamBlocks()
            .flowOn(Dispatchers.IO)
            .buffer()
            .catch { log.error("", it) }
            .onCompletion { log.info("stream fetch complete", it) }
            .onEach(fileOutput("../pio-testnet-1/json-data", moshi))
            .onEach(kafkaWriter(kafkaProducer, "test"))

        val kafkaStreamFlow = kafkaBlockSource(consumerProps("test"), "test").streamBlocks()
            .flowOn(Dispatchers.IO)
            .buffer()
            .catch { log.error("", it) }
            .onEach(kafkaFileOutput("../pio-testnet-1-kafka/json-data", moshi))


        launch {
//            chainStreamFlow.collectTo(consoleOutput(verbose, 1))
            chainStreamFlow.collect {log.info("recv from Chain: ${it} :: ${it.block} -> ${it.height}")}
        }

        launch {
//            kafkaStreamFlow.collectTo(consoleOutput(verbose, 1))
            kafkaStreamFlow.collect {
                log.info("recv from Kafka: ${it} :: ${it.block} -> ${it.height}")
            }//.acking {
            //    log.info("successfully acked:$it")
            //}
        }
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
