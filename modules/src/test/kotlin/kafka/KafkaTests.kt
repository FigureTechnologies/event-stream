package kafka

import io.confluent.kafka.serializers.KafkaJsonSerializer
import io.provenance.eventstream.stream.models.*
import io.provenance.eventstream.test.base.TestBase
import io.provenance.eventstream.test.utils.Defaults
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.RuntimeException
import java.time.OffsetDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaTests : TestBase() {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun kafkaWriterSendSuccess() = dispatcherProvider.runBlockingTest {

        val kafkaJsonSerializer = KafkaJsonSerializer<StreamBlock>()
        kafkaJsonSerializer.configure(mutableMapOf<String, StreamBlock>(), true)

        val mockProducer: MockProducer<String, StreamBlock> =
            MockProducer(false, StringSerializer(), kafkaJsonSerializer)

        val templates = Defaults.templates
        val blockResponse = templates.readAs(BlockResponse::class.java, "block/2270370.json")
        val blockResultsResponse = templates.readAs(BlockResultsResponse::class.java, "block_results/2270370.json")

        val blockEvents = blockResultsResponse!!.result.beginBlockEvents!!.map {
            BlockEvent(blockResultsResponse.result.height, OffsetDateTime.now(), it.type!!, it.attributes!!)
        }
        val streamBlock = StreamBlock(blockResponse!!.result!!.block!!, blockEvents, mutableListOf())
        assert(mockProducer.history().isEmpty())

        val expectedKey = "${blockResponse.result!!.block!!.header!!.chainId}.${blockResponse.result!!.block!!.header!!.height}"

        val record = kafkaWriter(mockProducer, "testTopic").send(streamBlock, expectedKey)

        mockProducer.completeNext()

        record.get()

        assert(mockProducer.history().size == 1)
        assert(mockProducer.history()[0].key() == expectedKey)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun kafkaWriterSendFail() = dispatcherProvider.runBlockingTest {

        val kafkaJsonSerializer = KafkaJsonSerializer<StreamBlock>()
        kafkaJsonSerializer.configure(mutableMapOf<String, StreamBlock>(), true)

        val mockProducer: MockProducer<String, StreamBlock> =
            MockProducer(false, StringSerializer(), kafkaJsonSerializer)

        val templates = Defaults.templates
        val blockResponse = templates.readAs(BlockResponse::class.java, "block/2270370.json")
        val blockResultsResponse = templates.readAs(BlockResultsResponse::class.java, "block_results/2270370.json")

        val blockEvents = blockResultsResponse!!.result.beginBlockEvents!!.map {
            BlockEvent(blockResultsResponse.result.height, OffsetDateTime.now(), it.type!!, it.attributes!!)
        }
        val streamBlock = StreamBlock(blockResponse!!.result!!.block!!, blockEvents, mutableListOf())

        val expectedKey = "${blockResponse.result!!.block!!.header!!.chainId}.${blockResponse.result!!.block!!.header!!.height}"

        val record = kafkaWriter(mockProducer, "testTopic").send(streamBlock, expectedKey)

        val e = RuntimeException()
        mockProducer.errorNext(e)

        try {
            record.get()
        } catch (ex: Exception) {
            assertEquals(e, ex.cause)
        }
    }
}
