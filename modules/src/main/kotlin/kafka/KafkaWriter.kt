package kafka

import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.eventstream.stream.models.StreamBlock
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata

fun kafkaWriter(producer: Producer<String, StreamBlock>, topicName: String) =
    KafkaWriter(producer, topicName)

@OptIn(ExperimentalStdlibApi::class)
class KafkaWriter(
    val producer: Producer<String, StreamBlock>,
    val topicName: String,
) : BlockSink {
    override suspend fun invoke(block: StreamBlock) {
        val key = "${block.block.header!!.chainId}.${block.block.header!!.time}"
        producer.send(ProducerRecord(topicName, key, block)) { m: RecordMetadata, e: Exception? ->
            when (e) {
                null -> println("Produced record to topic ${m.topic()} partition [${m.partition()}] @ offset ${m.offset()}")
                else -> e.printStackTrace()
            }
        }
    }
}
