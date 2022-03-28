package io.provenance.eventstream.stream.transformers

import io.provenance.eventstream.config.Options
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

/**
 * Query a block by height, returning any events associated with the block.
 *
 *  @param heightOrBlock Fetch a block, plus its events, by its height or the `Block` model itself.
 *  @param skipIfNoTxs If [skipIfNoTxs] is true, if the block at the given height has no transactions, null will
 *  be returned in its place.
 */
suspend fun queryBlock(
    height: Long,
    skipIfNoTxs: Boolean = true,
    historical: Boolean = false,
    fetcher: TendermintBlockFetcher,
    options: Options
): StreamBlockImpl? {
    val block: Block = fetcher.getBlock(height).block

    if (skipIfNoTxs && (block.data?.txs?.size ?: 0) == 0) {
        return null
    }

    return block?.run {
        val blockDatetime = header?.dateTime()
        val blockResponse = fetcher.getBlockResults(header!!.height)!!.result
        val blockEvents: List<BlockEvent> = blockResponse.blockEvents(blockDatetime)
        val txEvents: List<TxEvent> = blockResponse.txEvents(blockDatetime) { index: Int -> txData(index) ?: TxInfo() }
        val txErrors: List<TxError> = blockResponse.txErroredEvents(blockDatetime) { index: Int -> block.txData(index) }
        val streamBlock = StreamBlockImpl(this, blockEvents, blockResponse.txsResults, txEvents, txErrors, historical)
        val matchBlock = matchesBlockEvent(blockEvents, options)
        val matchTx = matchesTxEvent(txEvents, options)

        // ugly:
        if ((matchBlock == null && matchTx == null) || (matchBlock == null && matchTx != null && matchTx) || (matchBlock != null && matchBlock && matchTx == null) || (matchBlock != null && matchBlock && matchTx != null && matchTx)) {
            streamBlock
        } else {
            null
        }
    }
}

/**
 * Test if any block events match the supplied predicate.
 *
 * @return True or false if [Options.blockEventPredicate] matches a block-level event associated with a block.
 * If the return value is null, then [Options.blockEventPredicate] was never set.
 */
private fun <T : EncodedBlockchainEvent> matchesBlockEvent(blockEvents: List<T>, options: Options): Boolean? =
    options.blockEventPredicate?.let { p ->
        if (options.skipIfEmpty) {
            blockEvents.any { p(it.eventType) }
        } else {
            blockEvents.isEmpty() || blockEvents.any { p(it.eventType) }
        }
    }

/**
 * Test if any transaction events match the supplied predicate.
 *
 * @return True or false if [Options.txEventPredicate] matches a transaction-level event associated with a block.
 * If the return value is null, then [Options.txEventPredicate] was never set.
 */
private fun <T : EncodedBlockchainEvent> matchesTxEvent(txEvents: List<T>, options: Options): Boolean? =
    options.txEventPredicate?.let { p ->
        if (options.skipIfEmpty) {
            txEvents.any { p(it.eventType) }
        } else {
            txEvents.isEmpty() || txEvents.any { p(it.eventType) }
        }
    }
