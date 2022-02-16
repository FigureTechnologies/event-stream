package io.provenance.kafka.coroutine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

open class KafkaSource<K, V>(consumerProps: Map<String, Any>, topic: String) {

    private val incoming = kafkaChannel<K, V>(consumerProps, setOf(topic))

    fun beginFlow(): Flow<UnAckedConsumerRecordImpl<K, V>> {
        return incoming.receiveAsFlow().map { it as UnAckedConsumerRecordImpl<K, V> }
    }
}
