package tech.figure.eventstream.stream

import tech.figure.blockchain.stream.api.BlockSource
import tech.figure.eventstream.stream.models.StreamBlock
import io.provenance.kafka.coroutine.KafkaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes

fun kafkaBlockSource(consumerProperties: Map<String, Any>, topic: String): KafkaBlockSource {
    return KafkaBlockSource(consumerProperties, topic)
}

open class KafkaBlockSource(consumerProps: Map<String, Any>, topic: String) : BlockSource<KafkaStreamBlock> {
    private val deserializer = Serdes.ByteArray().deserializer()
    private val byteArrayProps = mapOf<String, Any>(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to deserializer.javaClass,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to deserializer.javaClass,
    )
    val kafkaSource: KafkaSource<ByteArray, ByteArray> = KafkaSource(consumerProps + byteArrayProps, topic)

    override fun streamBlocks(): Flow<KafkaStreamBlock> {
        return kafkaSource.beginFlow().map { KafkaStreamBlock(it) }
    }

    override suspend fun streamBlocks(from: Long?, toInclusive: Long?): Flow<StreamBlock> {
        TODO("Needs implementation")
    }
}
