package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockResultsResponseResult
import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

interface BlockSource {
    suspend fun streamBlocks(from: Long, toInclusive: Long? = Long.MAX_VALUE): Flow<StreamBlock>
//    suspend fun streamHistoricalBlocks(from: Long, toInclusive: Long? = Long.MAX_VALUE): Flow<StreamBlock>
//    suspend fun streamLiveBlocks(): Flow<StreamBlock>
}

open class BlockFetchException(m: String) : Exception(m)

data class BlockData(val block: Block, val blockResult: BlockResultsResponseResult)

@OptIn(ExperimentalCoroutinesApi::class)
interface BlockFetcher {
    suspend fun close()

    suspend fun getBlock(height: Long): BlockData

    suspend fun getBlockResults(heights: List<Long>): Flow<BlockData> =
        channelFlow { heights.forEach { send(getBlock(it)) } }

    suspend fun getBlocksResultsRanged(longRange: LongRange): Flow<BlockData> =
        getBlockResults(longRange.toList())
}