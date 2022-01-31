package io.provenance.eventstream

import io.provenance.eventstream.flow.kafka.acking
import io.provenance.eventstream.flow.kafka.kafkaChannel
import io.provenance.eventstream.stream.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import kotlin.time.ExperimentalTime

@OptIn(FlowPreview::class, ExperimentalTime::class)
@ExperimentalCoroutinesApi
fun main(args: Array<String>) {
    System.setProperty("kotlinx.coroutines.debug", "on")

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
    val groupId by parser.option(ArgType.String, fullName = "group", shortName = "g", description = "GroupID to connect to kafka as").required()
    val topic by parser.option(ArgType.String, fullName = "topic", shortName = "t", description = "Topic name to read metadata from").default("block.metadata.r1")
    val brokers by parser.option(ArgType.String, fullName = "broker", shortName = "n", description = "Comma separated kafka brokers to connect to for block stream").default("localhost:9092")
    parser.parse(args)

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

    val log = KotlinLogging.logger {}
    val kafkaFlow = kafkaChannel<ByteArray, ByteArray>(consumerProps, setOf(topic)).receiveAsFlow()
    runBlocking {
        log.info("consumerProps: $consumerProps")

        val f = kafkaFlow
            .flowOn(Dispatchers.IO)
            .buffer()
            .catch { log.error("", it) }
            .onCompletion { log.info("stream fetch complete", it) }
            .acking {
                log.info("successfully acked:$it")
                // Store somewhere here.
            }

        launch {
            f.collect {
                log.info { it }
            }
        }
    }
}
