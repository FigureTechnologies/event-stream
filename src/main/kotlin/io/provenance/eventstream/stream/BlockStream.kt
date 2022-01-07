package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockResultsResponseResult
import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow

interface BlockSource {
    suspend fun streamBlocks(from: Long?, toInclusive: Long? = Long.MAX_VALUE): Flow<StreamBlock>
}

open class BlockFetchException(m: String) : Exception(m)

data class BlockData(val block: Block, val blockResult: BlockResultsResponseResult)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
interface BlockFetcher {
    suspend fun getBlocks(heights: List<Long>, concurrency: Int = DEFAULT_CONCURRENCY): Flow<BlockData>
    suspend fun getCurrentHeight(): Long
    suspend fun getBlock(height: Long): BlockData
}