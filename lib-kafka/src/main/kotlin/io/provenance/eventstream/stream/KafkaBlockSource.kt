package io.provenance.eventstream.stream

import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.flow.kafka.kafkaChannel
import io.provenance.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

//<<<<<<< HEAD
fun kafkaBlockSource(
    consumerProperties: Map<String, Any>,
    topic: String
): KafkaBlockSource {
    return KafkaBlockSource(consumerProperties, topic)
}

open class KafkaBlockSource(consumerProps: Map<String, Any>, topic: String) : BlockSource<KafkaStreamBlock<String, StreamBlockImpl>> {

    private val incoming = kafkaChannel<String, StreamBlockImpl>(consumerProps, setOf(topic))

    override fun streamBlocks(): Flow<KafkaStreamBlock<String, StreamBlockImpl>> {
        return incoming.receiveAsFlow().map { KafkaStreamBlock(it) }
    }
//=======
//open class KafkaBlockSource<K, V>(consumerProps: Map<String, Any>, topic: String) : BlockSource<KafkaStreamBlock<K, V>> {
//    private val deserializer = Serdes.ByteArray().deserializer()
//    private val byteArrayProps = mapOf<String, Any>(
//        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to deserializer.javaClass,
//        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to deserializer.javaClass,
//    )
//
//    private val incoming = kafkaChannel<K, V>(consumerProps + byteArrayProps, setOf(topic))
//>>>>>>> d927adc5a3ad3c1b8818439663dc373bb917d26c

//    override fun streamBlocks(): Flow<KafkaStreamBlock<String, StreamBlockImpl>> {
//        return incoming.receiveAsFlow().map { KafkaStreamBlock(it) }
//    }
}