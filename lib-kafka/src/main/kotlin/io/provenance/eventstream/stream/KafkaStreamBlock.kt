package io.provenance.eventstream.stream

import io.provenance.eventstream.flow.kafka.UnAckedConsumerRecord
import io.provenance.eventstream.flow.kafka.toStreamBlock
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.TxEvent

data class KafkaStreamBlock<K, V>(
    val record: UnAckedConsumerRecord<ByteArray, ByteArray>,
) : StreamBlock {
    override val block: Block by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.block }
    override val blockEvents: List<BlockEvent> by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.blockEvents }
    override val txEvents: List<TxEvent> by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.txEvents }
    override val historical: Boolean by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.historical }
}