package io.provenance.eventstream.stream.transformers

import cosmos.base.abci.v1beta1.Abci
import io.provenance.eventstream.config.Options
import io.provenance.eventstream.extensions.dateTime
import io.provenance.eventstream.extensions.txHash
import io.provenance.eventstream.stream.clients.TendermintBlockFetcher
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.models.TxEvent
import io.provenance.eventstream.stream.models.TxError
import io.provenance.eventstream.stream.models.TxInfo
import io.provenance.eventstream.stream.models.EncodedBlockchainEvent
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.stream.models.extensions.txData
import io.provenance.eventstream.stream.models.extensions.blockEvents
import io.provenance.eventstream.stream.models.extensions.txErroredEvents
import io.provenance.eventstream.stream.models.extensions.txEvents
import io.provenance.eventstream.stream.models.extensions.txHash
import tendermint.abci.Types
import tendermint.types.BlockOuterClass

/**
 * Query a block by height, returning any events associated with the block.
 *
 *  @param heightOrBlock Fetch a block, plus its events, by its height or the `Block` model itself.
 *  @param skipIfNoTxs If [skipIfNoTxs] is true, if the block at the given height has no transactions, null will
 *  be returned in its place.
 */
//suspend fun queryBlock(
//    height: Long,
//    skipIfNoTxs: Boolean = true,
//    historical: Boolean = false,
//    fetcher: TendermintBlockFetcher,
//    options: Options
//): StreamBlockImpl? {
//<<<<<<< HEAD
//    val block: BlockOuterClass.Block = fetcher.getBlock(height)
//
//    if (skipIfNoTxs && (block?.data?.txsList?.size ?: 0) == 0) {
////=======
////    val block: Block = fetcher.getBlock(height).block
////
////    if (skipIfNoTxs && (block.data?.txs?.size ?: 0) == 0) {
////>>>>>>> 2e0d83082ec12150956bf9e7da1afafa5764b843
//        return null
//    }
//
//    return block.run {
//        val blockDatetime = header?.dateTime()
//<<<<<<< HEAD
//        val blockResponse = fetcher.getBlockResults(this)!!
//        val blockEvents: List<Types.Event> = blockResponse.blockEvents
//        val txEvents: List<Abci.StringEvent> = blockResponse.txEvents//blockResponse.txEvents(blockDatetime, height) { index: Int -> txHash(index) ?: "" }
//        val streamBlock = StreamBlockImpl(this, blockEvents, txEvents, historical)
//=======
//        val blockResponse = fetcher.getBlockResults(header!!.height)!!.result
//        val blockEvents: List<BlockEvent> = blockResponse.blockEvents(blockDatetime)
//        val txEvents: List<TxEvent> = blockResponse.txEvents(blockDatetime) { index: Int -> txData(index) ?: TxInfo() }
//        val txErrors: List<TxError> = blockResponse.txErroredEvents(blockDatetime) { index: Int -> block.txData(index) }
//        val streamBlock = StreamBlockImpl(this, blockEvents, blockResponse.txsResults, txEvents, txErrors, historical)
//>>>>>>> 2e0d83082ec12150956bf9e7da1afafa5764b843
//        val matchBlock = matchesBlockEvent(blockEvents, options)
//        val matchTx = matchesTxEvent(txEvents, options)
//
//        // ugly:
//        if ((matchBlock == null && matchTx == null) || (matchBlock == null && matchTx != null && matchTx) || (matchBlock != null && matchBlock && matchTx == null) || (matchBlock != null && matchBlock && matchTx != null && matchTx)) {
//            streamBlock
//        } else {
//            null
//        }
//    }
//}

/**
 * Test if any block events match the supplied predicate.
 *
 * @return True or false if [Options.blockEventPredicate] matches a block-level event associated with a block.
 * If the return value is null, then [Options.blockEventPredicate] was never set.
 */
private fun matchesBlockEvent(blockEvents: List<Types.Event>, options: Options): Boolean? =
    options.blockEventPredicate?.let { p ->
        if (options.skipIfEmpty) {
            blockEvents.any { p(it.type) }
        } else {
            blockEvents.isEmpty() || blockEvents.any { p(it.type) }
        }
    }

/**
 * Test if any transaction events match the supplied predicate.
 *
 * @return True or false if [Options.txEventPredicate] matches a transaction-level event associated with a block.
 * If the return value is null, then [Options.txEventPredicate] was never set.
 */
private fun matchesTxEvent(txEvents: List<Abci.StringEvent>, options: Options): Boolean? =
    options.txEventPredicate?.let { p ->
        if (options.skipIfEmpty) {
            txEvents.any { p(it.type) }
        } else {
            txEvents.isEmpty() || txEvents.any { p(it.type) }
        }
    }
