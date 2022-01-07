package io.provenance.eventstream.stream.clients

import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockResultsResponseResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

data class BlockData(val block: Block, val blockResult: BlockResultsResponseResult)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
interface BlockFetcher {
    suspend fun getCurrentHeight(): Long
    suspend fun getBlock(height: Long): BlockData
}

open class BlockFetchException(m: String) : Exception(m)
