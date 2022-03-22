package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.TxEvent
import io.provenance.kafka.coroutine.AckedConsumerRecord

class AckedKafkaStreamBlock<K, V>(record: AckedConsumerRecord<K, V>) : StreamBlock {
    private val streamBlock: StreamBlock by lazy { (record.value as ByteArray).toStreamBlock()!! }
    override val block: Block by lazy { streamBlock.block }
    override val blockEvents: List<BlockEvent> by lazy { streamBlock.blockEvents }
    override val txEvents: List<TxEvent> by lazy { streamBlock.txEvents }
    override val historical: Boolean by lazy { streamBlock.historical }
}
