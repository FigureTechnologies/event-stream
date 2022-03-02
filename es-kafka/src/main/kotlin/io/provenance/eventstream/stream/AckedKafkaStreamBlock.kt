package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.TxEvent
import io.provenance.kafka.coroutine.AckedConsumerRecord
import tendermint.types.BlockOuterClass

class AckedKafkaStreamBlock<K, V>(
    val record: AckedConsumerRecord<K, V>,
) : StreamBlock {
    override val block: BlockOuterClass.Block by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.block }
    override val blockEvents: List<BlockEvent> by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.blockEvents }
    override val txEvents: List<TxEvent> by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.txEvents }
    override val historical: Boolean by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.historical }
}
