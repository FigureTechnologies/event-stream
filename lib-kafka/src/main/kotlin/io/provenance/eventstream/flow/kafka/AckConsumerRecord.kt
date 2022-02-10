package io.provenance.eventstream.flow.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord

interface AckedConsumerRecord<K, V> {
    val record: ConsumerRecord<K, V>
}

class AckedConsumerRecordImpl<K, V>(
    override val record: ConsumerRecord<K, V>
) : AckedConsumerRecord<K, V>
