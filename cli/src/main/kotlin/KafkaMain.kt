package io.provenance.eventstream

import com.squareup.moshi.adapter
import io.provenance.eventstream.config.BatchConfig
import io.provenance.eventstream.config.Config
import io.provenance.eventstream.config.EventStreamConfig
import io.provenance.eventstream.config.RpcStreamConfig
import io.provenance.eventstream.config.StreamEventsFilterConfig
import io.provenance.eventstream.config.WebsocketStreamConfig
import io.provenance.eventstream.flow.kafka.acking
import io.provenance.eventstream.stream.*
import io.provenance.eventstream.stream.models.BlockHeader
import io.provenance.eventstream.stream.models.Event
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(FlowPreview::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
@ExperimentalCoroutinesApi
fun main(args: Array<String>) {
    System.setProperty("kotlinx.coroutines.debug", "on")

    val log = KotlinLogging.logger {}

    val parser = ArgParser("provenance-event-stream")
    val groupId by parser.option(ArgType.String, fullName = "group", shortName = "g", description = "GroupID to connect to kafka as").required()
    val topic by parser.option(ArgType.String, fullName = "topic", shortName = "t", description = "Topic name to read metadata from").default("block.metadata.r1")
    val brokers by parser.option(ArgType.String, fullName = "broker", shortName = "n", description = "Comma separated kafka brokers to connect to for block stream").default("localhost:9092")
    parser.parse(args)

    val txFilter = ""
    val blockFilter = ""
    val node = "rpc-3.test.provenance.io:26657"

    val esConfig = Config(
        eventStream = EventStreamConfig(
            skipEmpty = false,
            websocket = WebsocketStreamConfig("ws://$node"),
            rpc = RpcStreamConfig("http://$node"),
            filter = StreamEventsFilterConfig(
                txEvents = txFilter.split(",").filter { it.isNotBlank() }.toSet(),
                blockEvents = blockFilter.split(",").filter { it.isNotBlank() }.toSet()),
            batch = BatchConfig(16, timeoutMillis = Duration.ofSeconds(30).toMillis())
        ),
    )

    val options = EventStream.Options(
        batchSize = 16,
        fromHeight = null,
        toHeight = null,
        skipIfEmpty = false,
        txEventPredicate = { true },
        blockEventPredicate = { true },
        concurrency = 16,
    )

    val consumerProps = mapOf<String, Any>(
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to brokers,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 16,
        ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to 10_000,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
    )

    val okClient = defaultOkHttpClient()
    val nodeFlow = defaultEventStream(esConfig, options, okClient).streamBlocks()
        .flowOn(Dispatchers.IO)
        .buffer()
        .catch { log.error("", it) }
        .onCompletion { log.info("stream fetch complete", it) }

    val kafkaFlow = KafkaBlockSource<ByteArray, ByteArray>(consumerProps, topic).streamBlocks()
        .flowOn(Dispatchers.IO)
        .buffer()
        .catch { log.error("", it) }
        .onCompletion { log.info("stream fetch complete", it) }
        .acking {
            log.info("successfully acked:$it")
            // Store somewhere here.
        }

    val producerConfig = mapOf<String, Any>(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to brokers,
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
    )

    val kafkaProducer = KafkaProducer<ByteArray, ByteArray>(producerConfig)

    data class KafkaMetadata(val header: BlockHeader, val blockEvents: Map<String, List<Event>>, val txEvents: Map<String, List<Event>>) {
        fun toByteArray(): ByteArray = toString().toByteArray()
    }

    runBlocking {
        launch {
            nodeFlow.collect {
                val blockEvents = it.blockEvents.associate { it.eventType to it.attributes }
                val txEvents = it.txEvents.associate { it.eventType to it.attributes }
                val metadata = KafkaMetadata(it.block.header!!, blockEvents, txEvents)
                kafkaProducer.send(
                    ProducerRecord("block.metadata.r1", it.height.toString().toByteArray(), metadata.toByteArray())
                ).get()
            }
        }

        launch {
            kafkaFlow.collect {
                log.info { it }
            }
        }
    }
}
