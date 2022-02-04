package io.provenance.eventstream.stream

import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.flow.kafka.kafkaChannel
import io.provenance.eventstream.flow.kafka.toStreamBlock
import io.provenance.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes

fun kafkaBlockSource(
    consumerProperties: Map<String, Any>,
    topic: String
): KafkaBlockSource {
    return KafkaBlockSource(consumerProperties, topic)
}

open class KafkaBlockSource(consumerProps: Map<String, Any>, topic: String) : BlockSource<KafkaStreamBlock<String, StreamBlockImpl>> {
    private val deserializer = Serdes.ByteArray().deserializer()
    private val byteArrayProps = mapOf<String, Any>(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to deserializer.javaClass,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to deserializer.javaClass,
    )

    private val incoming = kafkaChannel<ByteArray, ByteArray>(consumerProps + byteArrayProps, setOf(topic))

    override fun streamBlocks(): Flow<KafkaStreamBlock<String, StreamBlockImpl>> {
        return incoming.receiveAsFlow().map { KafkaStreamBlock(it) }
    }
}