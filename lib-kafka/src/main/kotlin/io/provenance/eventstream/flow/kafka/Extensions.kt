package io.provenance.eventstream.flow.kafka

import io.provenance.eventstream.stream.AckedKafkaStreamBlock
import io.provenance.eventstream.stream.KafkaStreamBlock
import io.provenance.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.apache.kafka.clients.consumer.ConsumerRecord

internal fun <T, L : Iterable<T>> L.ifEmpty(block: () -> L): L = if (count() == 0) block() else this

//fun <K, V> Flow<KafkaStreamBlock<K, V>>.acking(block: (StreamBlock<K, V>) -> Unit): Flow<AckedConsumerRecord<K, V>> {
//    return flow {
//        collect {
//            block(it.record.record)
//            it.record.ack()
//        }
//    }
//}

fun Flow<KafkaStreamBlock<String, StreamBlockImpl>>.acking(block: (KafkaStreamBlock<String, StreamBlockImpl>) -> Unit): Flow<AckedKafkaStreamBlock<String, StreamBlockImpl>> {
    return flow {
        collect {
//            block(it.record.record)
            val ackedConsumerRecordImpl = it.record.ack()
            emit(AckedKafkaStreamBlock(ackedConsumerRecordImpl))
        }
    }
}

//private fun <StreamBlock> Flow<KafkaStreamBlock<String, StreamBlockImpl>>.acking(block: (KafkaStreamBlock<String, StreamBlockImpl>) -> Unit): AckedKafkaStreamBlock<String, StreamBlockImpl> {
//    return flow<AckedConsumerRecordImpl<String, StreamBlockImpl> {
//        collect {
//            block(it.record.record)
//            val ackedConsumerRecordImpl = it.record.ack()
//            emit(AckedKafkaStreamBlock(ackedConsumerRecordImpl))
//        }
//    }
//}