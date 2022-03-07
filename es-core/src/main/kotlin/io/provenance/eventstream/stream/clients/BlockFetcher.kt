package io.provenance.eventstream.stream.clients

import io.provenance.eventstream.stream.models.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow

interface BlockSource {
    suspend fun streamBlocks(from: Long?, toInclusive: Long? = Long.MAX_VALUE): Flow<StreamBlock>
}

data class BlockData(val block: Block, val blockResult: BlockResultsResponseResult)

open class BlockFetchException(m: String) : Exception(m)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
interface BlockFetcher {
    suspend fun getBlocksMeta(min: Long, max: Long): List<BlockMeta>?
    suspend fun getCurrentHeight(): Long?
    suspend fun getBlock(height: Long): BlockData
    suspend fun getBlockResults(height: Long): BlockResultsResponse?
    suspend fun getBlocks(heights: List<Long>, concurrency: Int = DEFAULT_CONCURRENCY): Flow<BlockData>
}
