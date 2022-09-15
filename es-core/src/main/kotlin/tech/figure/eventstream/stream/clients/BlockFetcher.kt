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

data class BlockData(val block: Block, val blockResult: BlockResultsResponseResult) {
    val height = block.header!!.height
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
