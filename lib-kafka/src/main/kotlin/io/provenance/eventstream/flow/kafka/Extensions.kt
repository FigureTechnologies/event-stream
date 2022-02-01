package io.provenance.eventstream.flow.kafka

import io.provenance.eventstream.stream.KafkaStreamBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.apache.kafka.clients.consumer.ConsumerRecord

internal fun <T, L : Iterable<T>> L.ifEmpty(block: () -> L): L = if (count() == 0) block() else this

fun <K, V> Flow<KafkaStreamBlock<K, V>>.acking(block: (ConsumerRecord<K, V>) -> Unit): Flow<AckedConsumerRecord<K, V>> {
    return flow {
        collect {
            block(it.record.record)
            it.record.ack()
        }
    }
}