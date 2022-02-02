package io.provenance.eventstream.stream

import io.provenance.eventstream.flow.kafka.AckedConsumerRecord
import io.provenance.eventstream.flow.kafka.AckedConsumerRecordImpl
import io.provenance.eventstream.flow.kafka.toStreamBlock
import io.provenance.eventstream.stream.models.*

class AckedKafkaStreamBlock <K, V> (
    val record: AckedConsumerRecord<K, V>,
    ) : StreamBlock {
        override val block: Block by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.block }
        override val blockEvents: List<BlockEvent> by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.blockEvents }
        override val txEvents: List<TxEvent> by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.txEvents }
        override val historical: Boolean by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.historical }
}