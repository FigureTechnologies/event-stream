package tech.figure.eventstream.stream

import tech.figure.eventstream.stream.models.Block
import tech.figure.eventstream.stream.models.BlockEvent
import tech.figure.eventstream.stream.models.StreamBlock
import tech.figure.eventstream.stream.models.BlockResultsResponseResultTxsResults
import tech.figure.eventstream.stream.models.TxError
import tech.figure.eventstream.stream.models.TxEvent
import io.provenance.kafka.coroutine.AckedConsumerRecord

class AckedKafkaStreamBlock<K, V>(record: AckedConsumerRecord<K, V>) : StreamBlock {
    private val streamBlock: StreamBlock by lazy { (record.value as ByteArray).toStreamBlock()!! }
    override val block: Block by lazy { streamBlock.block }
    override val blockEvents: List<tech.figure.eventstream.stream.models.BlockEvent> by lazy { streamBlock.blockEvents }
    override val txEvents: List<TxEvent> by lazy { streamBlock.txEvents }
    override val historical: Boolean by lazy { streamBlock.historical }
    override val blockResult: List<BlockResultsResponseResultTxsResults>? by lazy { streamBlock.blockResult }
    override val txErrors: List<TxError> by lazy { streamBlock.txErrors }
}
