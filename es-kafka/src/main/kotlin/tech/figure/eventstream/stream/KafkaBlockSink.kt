package tech.figure.eventstream.stream

import tech.figure.blockchain.stream.api.BlockSink
import tech.figure.eventstream.stream.models.StreamBlockImpl
import tech.figure.eventstream.stream.models.StreamBlock
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serdes

fun kafkaBlockSink(producerProps: Map<String, Any>, topicName: String, kafkaProducer: Producer<ByteArray, ByteArray>? = null): KafkaBlockSink =
    KafkaBlockSink(producerProps, topicName, kafkaProducer)

@OptIn(ExperimentalStdlibApi::class)
class KafkaBlockSink(
    producerProps: Map<String, Any>,
    private val topicName: String,
    kafkaProducer: Producer<ByteArray, ByteArray>? = null,
) : BlockSink {
    private val serializer = Serdes.ByteArray().serializer()
    private val byteArrayProps = mapOf<String, Any>(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
    )

    private val producer = kafkaProducer ?: KafkaProducer(producerProps + byteArrayProps)

    override suspend fun invoke(block: StreamBlock) {
        val key = "${block.block.header!!.chainId}.${block.height}"
        val record = ProducerRecord(
            topicName,
            key.toByteArray(),
            (block as StreamBlockImpl).toByteArray()!!,
        )
        producer.send(record)
    }
}
