package io.provenance.eventstream

import io.provenance.eventstream.stream.kafkaBlockSink
import io.provenance.eventstream.stream.models.*
import io.provenance.eventstream.test.base.TestBase
import io.provenance.eventstream.test.utils.Defaults
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.Serdes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
            BlockEvent(blockResultsResponse.result.height, OffsetDateTime.now(), it.type!!, it.attributes!!)
        }
        val streamBlock = StreamBlockImpl(blockResponse!!.result!!.block!!, blockEvents, mutableListOf())
        assert(mockProducer.history().isEmpty())

        val expectedKey = "${blockResponse.result!!.block!!.header!!.chainId}.${blockResponse.result!!.block!!.header!!.height}"

        val record = kafkaBlockSink(producerProps, "testTopic").send(streamBlock, expectedKey, mockProducer)

        mockProducer.completeNext()

        record.get()

        assert(mockProducer.history().size == 1)
        assert(mockProducer.history()[0].key().decodeToString() == expectedKey)
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
        )

        val mockProducer: MockProducer<ByteArray, ByteArray> =
            MockProducer(false, serializer, serializer)

        val templates = Defaults.templates
        val blockResponse = templates.readAs(BlockResponse::class.java, "block/2270370.json")
        val blockResultsResponse = templates.readAs(BlockResultsResponse::class.java, "block_results/2270370.json")

        val blockEvents = blockResultsResponse!!.result.beginBlockEvents!!.map {
            BlockEvent(blockResultsResponse.result.height, OffsetDateTime.now(), it.type!!, it.attributes!!)
        }
        val streamBlock = StreamBlockImpl(blockResponse!!.result!!.block!!, blockEvents, mutableListOf())

        val expectedKey = "${blockResponse.result!!.block!!.header!!.chainId}.${blockResponse.result!!.block!!.header!!.height}"

        val record = kafkaBlockSink(producerProps, "testTopic").send(streamBlock, expectedKey, mockProducer)

        val e = RuntimeException()
        mockProducer.errorNext(e)

        try {
            record.get()
        } catch (ex: Exception) {
            assertEquals(e, ex.cause)
        }
    }
}