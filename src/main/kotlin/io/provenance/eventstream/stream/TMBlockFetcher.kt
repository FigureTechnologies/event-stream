package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.clients.TendermintServiceClient
import kotlinx.coroutines.ExperimentalCoroutinesApi

class TMBlockFetcher(private val tmClient: TendermintServiceClient) : BlockFetcher {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getBlock(height: Long): BlockData {
        val block = tmClient.block(height).result?.block
            ?: throw BlockFetchException("failed to fetch height:$height")

        val blockResult = tmClient.blockResults(height).result
        return BlockData(block, blockResult)
    }
}