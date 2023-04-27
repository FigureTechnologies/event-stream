package tech.figure.eventstream

import tech.figure.eventstream.stream.kafkaBlockSink
import tech.figure.eventstream.stream.models.BlockEvent
import tech.figure.eventstream.stream.models.BlockResponse
import tech.figure.eventstream.stream.models.BlockResultsResponse
import tech.figure.eventstream.stream.models.StreamBlockImpl
import tech.figure.eventstream.test.base.TestBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serdes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tech.figure.eventstream.utils.Defaults
import java.lang.RuntimeException
import java.time.OffsetDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaSinkTests : TestBase() {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testKafkaSinkSendSuccess() = dispatcherProvider.runBlockingTest {
        val serializer = Serdes.ByteArray().serializer()

        val producerProps = mapOf(
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            CommonClientConfigs.CLIENT_ID_CONFIG to ("test0"),
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
        )

        val mockProducer: MockProducer<ByteArray, ByteArray> =
            MockProducer(false, serializer, serializer)

        val templates = Defaults.templates
        val blockResponse = templates.readAs(BlockResponse::class.java, "block/2270370.json")
        val blockResultsResponse = templates.readAs(BlockResultsResponse::class.java, "block_results/2270370.json")

        val blockEvents = blockResultsResponse!!.result.beginBlockEvents!!.map {
            BlockEvent(
                blockResultsResponse.result.height,
                OffsetDateTime.now(),
                it.type!!,
                it.attributes!!,
            )
        }
        val streamBlock = StreamBlockImpl(blockResponse!!.result!!.block!!, blockEvents, mutableListOf(), mutableListOf(), mutableListOf())
        assert(mockProducer.history().isEmpty())

        val expectedKey =
            "${blockResponse.result!!.block!!.header!!.chainId}.${blockResponse.result!!.block!!.header!!.height}"

        kafkaBlockSink(producerProps, "testTopic", mockProducer).also {
            it.invoke(streamBlock)
        }

        mockProducer.completeNext()

        assertEquals(mockProducer.history().size, 1)
        assertEquals(mockProducer.history()[0].key().decodeToString(), expectedKey)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testKafkaSinkSendFail() = dispatcherProvider.runBlockingTest {
        val serializer = Serdes.ByteArray().serializer()
        val producerProps = mapOf(
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            CommonClientConfigs.CLIENT_ID_CONFIG to ("test0"),
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
        )

        val mockProducer: MockProducer<ByteArray, ByteArray> =
            MockProducer(false, serializer, serializer)

        val templates = Defaults.templates
        val blockResponse = templates.readAs(BlockResponse::class.java, "block/2270370.json")
        val blockResultsResponse = templates.readAs(BlockResultsResponse::class.java, "block_results/2270370.json")

        val blockEvents = blockResultsResponse!!.result.beginBlockEvents!!.map {
            BlockEvent(
                blockResultsResponse.result.height,
                OffsetDateTime.now(),
                it.type!!,
                it.attributes!!,
            )
        }
        val streamBlock = StreamBlockImpl(blockResponse!!.result!!.block!!, blockEvents, mutableListOf(), mutableListOf(), mutableListOf())

        val e = RuntimeException()
        mockProducer.errorNext(e)

        try {
            kafkaBlockSink(producerProps, "testTopic", mockProducer).also {
                it.invoke(streamBlock)
            }
        } catch (ex: Exception) {
            assertEquals(e, ex.cause)
        }
    }
}
