package io.provenance.eventstream.stream

import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.eventstream.flow.kafka.KafkaSink
import io.provenance.eventstream.flow.kafka.toByteArray
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.models.StreamBlock
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serdes

fun kafkaBlockSink(producerProps: Map<String, Any>, topicName: String): KafkaBlockSink =
    KafkaBlockSink(producerProps, topicName)

@OptIn(ExperimentalStdlibApi::class)
class KafkaBlockSink(
    producerProps: Map<String, Any>,
    val topicName: String,
) : BlockSink {
    private val serializer = Serdes.ByteArray().serializer()
    private val byteArrayProps = mapOf<String, Any>(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
    )

    val kafkaSink: KafkaSink<ByteArray, ByteArray> = KafkaSink(producerProps + byteArrayProps, topicName)

    override suspend fun invoke(block: StreamBlock) {
        val key = "${block.block.header!!.chainId}.${block.height}"
        kafkaSink.send((block as StreamBlockImpl).toByteArray()!!, key.toByteArray())
    }
}
