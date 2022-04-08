package io.provenance.eventstream.stream.clients

import io.provenance.eventstream.stream.TendermintServiceClient
import io.provenance.eventstream.stream.models.BlockMeta
import io.provenance.eventstream.stream.models.BlockResultsResponse
import tendermint.types.BlockOuterClass
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transform
import org.slf4j.LoggerFactory

class TendermintBlockFetcher(
    val tendermintServiceClient: TendermintServiceClient
) : BlockFetcher {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun getBlocksMeta(min: Long, max: Long): List<BlockMeta>? {
        return tendermintServiceClient.blockchain(min, max).result?.blockMetas
    }

    override suspend fun getCurrentHeight(): Long? {
        return tendermintServiceClient.getHeight()
    }

    override suspend fun getBlock(height: Long): BlockData {
        log.trace("getBlock($height)")
        val block = tendermintServiceClient.block(height)
            ?: throw BlockFetchException("failed to fetch height:$height")
        log.trace("getBlock($height) complete")

        log.trace("get block result($height)")
        val blockResult = tendermintServiceClient.blockResults(block)
        log.trace("get block result($height) complete")
//        return tendermintServiceClient.block(height)
        return BlockData(block, blockResult)

//        return tendermintServiceClient.abciInfo().result?.response?.lastBlockHeight
//            ?: throw BlockFetchException("failed to fetch current block height")
//    }

//    override suspend fun getBlock(height: Long): BlockData {
//        log.trace("getBlock($height)")
//        val block = tendermintServiceClient.block(height).result?.block
//            ?: throw BlockFetchException("failed to fetch height:$height")
//        log.trace("getBlock($height) complete")
//
//        log.trace("get block result($height)")
//        val blockResult = tendermintServiceClient.blockResults(height).result
//        log.trace("get block result($height) complete")
//        return BlockData(block, blockResult)
    }
    override suspend fun getBlockResults(block: BlockOuterClass.Block): io.provenance.eventstream.stream.clients.BlockResultsResponse {
        return tendermintServiceClient.blockResults(block)
    }

    override suspend fun getBlockResults(height: Long): io.provenance.eventstream.stream.clients.BlockResultsResponse {
        TODO("Not yet implemented")
    }

    @OptIn(FlowPreview::class)
    override suspend fun getBlocks(heights: List<Long>, concurrency: Int): Flow<BlockData> =
        heights.chunked(concurrency).asFlow().transform { chunkOfHeights: List<Long> ->
            emitAll(
                coroutineScope {
                    // Concurrently process <concurrency> blocks at a time:
                    chunkOfHeights.map { height -> async { getBlock(height) } }.awaitAll()
                }.asFlow()
            )
        }
}
