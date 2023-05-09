package tech.figure.eventstream.stream.clients

import tech.figure.eventstream.stream.models.Block
import tech.figure.eventstream.stream.models.BlockMeta
import tech.figure.eventstream.stream.models.BlockResultsResponse
import tech.figure.eventstream.stream.models.BlockResultsResponseResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import tech.figure.eventstream.stream.models.StreamBlock
import tech.figure.eventstream.stream.models.StreamBlockImpl
import tech.figure.eventstream.stream.models.TxEvent
import tech.figure.eventstream.stream.models.blockEvents
import tech.figure.eventstream.stream.models.dateTime
import tech.figure.eventstream.stream.models.txData
import tech.figure.eventstream.stream.models.txErroredEvents
import tech.figure.eventstream.stream.models.txEvents

/**
 * A data class encapsulating a Provenance block, containing metadata about the block as well as the actual transaction
 * data itself.
 */
data class BlockData(val block: Block, val blockResult: BlockResultsResponseResult) {
    /**
     * The height of the block.
     *
     * @return The height of the block.
     */
    val height: Long = block.header!!.height

    /**
     * List all transaction events occurring in the block.
     *
     * @return A list of all events associated with transactions that are a part of this block.
     */
    fun txEvents(): List<TxEvent> = blockResult.txEvents(block.dateTime()) { index -> block.txData(index) }

    /**
     * Converts this block data container into an instance of [StreamBlock].
     *
     * @return A [StreamBlock] instance.
     */
    fun toStreamBlock(): StreamBlock {
        val blockDatetime = block.header?.dateTime()
        val blockEvents = blockResult.blockEvents(blockDatetime)
        val blockTxResults = blockResult.txsResults
        val txEvents = blockResult.txEvents(blockDatetime) { index: Int -> block.txData(index) }
        val txErrors = blockResult.txErroredEvents(blockDatetime) { index: Int -> block.txData(index) }
        return StreamBlockImpl(block = block, blockEvents = blockEvents, blockResult = blockTxResults, txEvents = txEvents, txErrors = txErrors)
    }
}

open class BlockFetchException(m: String) : Exception(m)

@OptIn(FlowPreview::class)
interface BlockFetcher {
    suspend fun getBlocksMeta(min: Long, max: Long): List<BlockMeta>?
    suspend fun getCurrentHeight(): Long?
    suspend fun getBlock(height: Long): BlockData
    suspend fun getBlockResults(height: Long): BlockResultsResponse?
    suspend fun getBlocks(heights: List<Long>, concurrency: Int = DEFAULT_CONCURRENCY, context: CoroutineContext = EmptyCoroutineContext): Flow<BlockData>
}
