package io.provenance.eventstream.stream

import cosmos.base.abci.v1beta1.Abci
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.TxError
import io.provenance.eventstream.stream.models.TxEvent
import io.provenance.kafka.coroutine.AckedConsumerRecord
import tendermint.abci.Types
import tendermint.types.BlockOuterClass

//<<<<<<< HEAD
//class AckedKafkaStreamBlock<K, V>(
//    val record: AckedConsumerRecord<K, V>,
//) : StreamBlock {
//    override val block: BlockOuterClass.Block by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.block }
//    override val blockEvents: List<Types.Event> by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.blockEvents }
//    override val txEvents: List<Abci.StringEvent> by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.txEvents }
//    override val historical: Boolean by lazy { (record.record.value() as ByteArray).toStreamBlock()!!.historical }
//=======
class AckedKafkaStreamBlock<K, V>(record: AckedConsumerRecord<K, V>) : StreamBlock {
    private val streamBlock: StreamBlock by lazy { (record.value as ByteArray).toStreamBlock()!! }
    override val block: BlockOuterClass.Block by lazy { streamBlock.block }
    override val blockEvents: List<Types.Event> by lazy { streamBlock.blockEvents }
    override val txEvents: List<TxEvent> by lazy { streamBlock.txEvents }
    override val historical: Boolean by lazy { streamBlock.historical }
    override val blockResult: List<Abci.StringEvent>? by lazy { streamBlock.blockResult }
    override val txErrors: List<TxError> by lazy { streamBlock.txErrors }
}
