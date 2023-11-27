package tech.figure.eventstream.stream

import tech.figure.eventstream.stream.models.Block
import tech.figure.eventstream.stream.models.StreamBlock
import tech.figure.eventstream.stream.models.BlockResultsResponseResultTxsResultsInner
import tech.figure.eventstream.stream.models.TxError
import tech.figure.eventstream.stream.models.TxEvent
import io.provenance.kafka.coroutine.AckedConsumerRecord
import io.provenance.kafka.coroutine.UnAckedConsumerRecord
import tech.figure.eventstream.stream.models.BlockEvent

class KafkaStreamBlock(
    private val record: UnAckedConsumerRecord<ByteArray, ByteArray>,
) : StreamBlock {
    private val streamBlock: StreamBlock by lazy { record.value.toStreamBlock()!! }
    override val block: Block by lazy { streamBlock.block }
    override val blockEvents: List<BlockEvent> by lazy { streamBlock.blockEvents }
    override val txEvents: List<TxEvent> by lazy { streamBlock.txEvents }
    override val historical: Boolean by lazy { streamBlock.historical }
    override val blockResult: List<BlockResultsResponseResultTxsResultsInner>? by lazy { streamBlock.blockResult }
    override val txErrors: List<TxError> by lazy { streamBlock.txErrors }

    suspend fun ack(): AckedConsumerRecord<ByteArray, ByteArray> {
        return record.ack()
    }
}
