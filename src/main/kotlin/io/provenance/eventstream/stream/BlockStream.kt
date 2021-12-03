package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockResultsResponseResult
import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map

interface BlockSource {
    suspend fun streamBlocks(from: Long, toInclusive: Long? = Long.MAX_VALUE): Flow<StreamBlock>
//    suspend fun streamHistoricalBlocks(from: Long, toInclusive: Long? = Long.MAX_VALUE): Flow<StreamBlock>
//    suspend fun streamLiveBlocks(): Flow<StreamBlock>
}

open class BlockFetchException(m: String) : Exception(m)

data class BlockData(val block: Block, val blockResult: BlockResultsResponseResult)

interface BlockFetcher {
    suspend fun getBlock(height: Long): BlockData
    suspend fun getBlockResults(heights: Flow<Long>) = heights.map { getBlock(it) }
    suspend fun getBlocksResultsRanged(longRange: LongRange) = getBlockResults(longRange.asFlow())
}