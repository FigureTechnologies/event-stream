package io.provenance.eventstream.stream.clients

import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockMeta
import tendermint.types.BlockOuterClass
import io.provenance.eventstream.stream.clients.BlockResultsResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow

data class BlockData(val block: BlockOuterClass.Block, val blockResult: BlockResultsResponse)

open class BlockFetchException(m: String) : Exception(m)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
interface BlockFetcher {
    suspend fun getBlocksMeta(min: Long, max: Long): List<BlockMeta>?
    suspend fun getCurrentHeight(): Long?
    suspend fun getBlock(height: Long): BlockData
    suspend fun getBlockResults(block: BlockOuterClass.Block): io.provenance.eventstream.stream.clients.BlockResultsResponse
//    suspend fun getBlock(height: Long): BlockData
    suspend fun getBlockResults(height: Long): io.provenance.eventstream.stream.clients.BlockResultsResponse
    suspend fun getBlocks(heights: List<Long>, concurrency: Int = DEFAULT_CONCURRENCY): Flow<BlockData>
}
