package io.provenance.eventstream.stream.clients

import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.flow.Flow

/**
 *
 */
interface BlockFetcher {
    /**
     * Query a block by height, returning any events associated with the block.
     *
     *  @param height Fetch a block, plus its events, by its height.
     *  be returned in its place.
     */
    suspend fun queryBlock(height: Long): StreamBlock?

    /***
     * Query a collections of blocks by their heights.
     *
     * Note: it is assumed the specified blocks already exists. No check will be performed to verify existence!
     *
     * @param blockHeights The heights of the blocks to query, along with optional metadata to attach to the fetched
     *  block data.
     * @return A Flow of found historical blocks along with events associated with each block, if any.
     */
    fun queryBlocks(blockHeights: Iterable<Long>, batchSize: Int): Flow<StreamBlock>
}