package io.provenance.eventstream.stream.clients

import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockMeta
import io.provenance.eventstream.stream.models.BlockResultsResponse
import io.provenance.eventstream.stream.models.BlockResultsResponseResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

data class BlockData(val block: Block, val blockResult: BlockResultsResponseResult, var historical: Boolean = false) {
    val height = block.header!!.height
}

open class BlockFetchException(m: String) : Exception(m)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
interface BlockFetcher {
    suspend fun getBlocksMeta(min: Long, max: Long): List<BlockMeta>?
    suspend fun getCurrentHeight(): Long?
    suspend fun getBlock(height: Long): BlockData
    suspend fun getBlockResults(height: Long): BlockResultsResponse?
    suspend fun getBlocks(heights: List<Long>, concurrency: Int = DEFAULT_CONCURRENCY, context: CoroutineContext = EmptyCoroutineContext): Flow<BlockData>
}
