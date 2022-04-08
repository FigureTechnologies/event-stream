package io.provenance.eventstream.stream

import cosmos.base.abci.v1beta1.Abci
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults
import io.provenance.eventstream.stream.models.TxError
import io.provenance.eventstream.stream.models.TxEvent
import io.provenance.kafka.coroutine.AckedConsumerRecord
import io.provenance.kafka.coroutine.UnAckedConsumerRecord
import tendermint.abci.Types
import tendermint.types.BlockOuterClass

class KafkaStreamBlock(
    private val record: UnAckedConsumerRecord<ByteArray, ByteArray>,
) : StreamBlock {
//    private val streamBlock: StreamBlock by lazy { record.value.toStreamBlock()!! }
//    override val block: Block by lazy { streamBlock.block }
//    override val blockEvents: List<BlockEvent> by lazy { streamBlock.blockEvents }
//    override val txEvents: List<TxEvent> by lazy { streamBlock.txEvents }
//    override val historical: Boolean by lazy { streamBlock.historical }
//    override val blockResult: List<BlockResultsResponseResultTxsResults>? by lazy { streamBlock.blockResult }
//    override val txErrors: List<TxError> by lazy { streamBlock.txErrors }

    private val streamBlock: StreamBlock by lazy { (record.value).toStreamBlock()!! }
    override val block: BlockOuterClass.Block by lazy { streamBlock.block }
    override val blockEvents: List<Types.Event> by lazy { streamBlock.blockEvents }
    override val txEvents: List<TxEvent> by lazy { streamBlock.txEvents }
    override val historical: Boolean by lazy { streamBlock.historical }
    override val blockResult: List<Abci.StringEvent>? by lazy { streamBlock.blockResult }
    override val txErrors: List<TxError> by lazy { streamBlock.txErrors }
    suspend fun ack(): AckedConsumerRecord<ByteArray, ByteArray> {
        return record.ack()
    }
}
