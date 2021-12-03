package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.clients.TendermintServiceClient
import kotlinx.coroutines.*

@OptIn(FlowPreview::class)
class TMBlockFetcher(private val tmClient: TendermintServiceClient) : BlockFetcher {
    override suspend fun getBlock(height: Long): BlockData = coroutineScope {
        val block = async { tmClient.block(height).result?.block }
        val blockResults = async { tmClient.blockResults(height).result }
        BlockData(
            block.await() ?: throw BlockFetchException("unable to fetch block height:$height"),
            blockResults.await() ?: throw BlockFetchException("unable to fetch block height:$height"),
        )
    }
}