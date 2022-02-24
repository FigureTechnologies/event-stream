package io.provenance.eventstream.stream.clients

import io.provenance.eventstream.stream.TendermintServiceClient
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockMeta
import io.provenance.eventstream.stream.models.BlockResultsResponse

class TendermintBlockFetcher(
    val tendermintServiceClient: TendermintServiceClient
) : BlockFetcher {

    override suspend fun getBlocksMeta(min: Long, max: Long): List<BlockMeta>? {
        return tendermintServiceClient.blockchain(min, max).result?.blockMetas
    }

    override suspend fun getCurrentHeight(): Long? {
        return tendermintServiceClient.abciInfo().result?.response?.lastBlockHeight
    }

    override suspend fun getBlock(height: Long): Block? {
        return tendermintServiceClient.block(height).result?.block
    }
    override suspend fun getBlockResults(height: Long): BlockResultsResponse? {
        return tendermintServiceClient.blockResults(height)
    }
}
