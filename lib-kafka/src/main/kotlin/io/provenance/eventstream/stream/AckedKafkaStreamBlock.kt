package io.provenance.eventstream.stream

import io.provenance.eventstream.flow.kafka.AckedConsumerRecord
import io.provenance.eventstream.flow.kafka.AckedConsumerRecordImpl
import io.provenance.eventstream.stream.models.*

class AckedKafkaStreamBlock <K, V> (
    val record: AckedConsumerRecord<K, V>,
    ) : StreamBlock {
        override val block: Block by lazy { (record.record.value() as StreamBlockImpl).block }
        override val blockEvents: List<BlockEvent> by lazy { (record.record.value() as StreamBlockImpl).blockEvents }
        override val txEvents: List<TxEvent> by lazy { (record.record.value() as StreamBlockImpl).txEvents }
        override val historical: Boolean by lazy { (record.record.value() as StreamBlockImpl).historical }
}