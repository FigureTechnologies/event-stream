package kafka

import io.confluent.kafka.serializers.KafkaJsonSerializer
import io.provenance.eventstream.stream.models.*
import io.provenance.eventstream.test.base.TestBase
import io.provenance.eventstream.test.utils.Defaults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.OffsetDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaTests : TestBase() {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun kafka_writer_send_success() = dispatcherProvider.runBlockingTest {

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
        kafkaWriter(mockProducer, "testTopic").invoke(streamBlock)

        val expectedKey = "${blockResponse.result!!.block!!.header!!.chainId}.${blockResponse.result!!.block!!.header!!.time}"
        assert(mockProducer.history().size == 1)
        assert(mockProducer.history()[0].key() == expectedKey)
    }
}
