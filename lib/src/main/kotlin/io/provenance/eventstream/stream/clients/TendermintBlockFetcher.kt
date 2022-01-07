package io.provenance.eventstream.stream.clients

import io.provenance.eventstream.stream.apis.ABCIApi
import io.provenance.eventstream.stream.apis.InfoApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * An OpenAPI generated client designed to interact with the Tendermint RPC API.
 *
 * All requests and responses are HTTP+JSON.
 *
 * @property rpcUrlBase The base URL of the Tendermint RPC API to use when making requests.
 */
class TendermintBlockFetcher(node: String, uri: URI = URI("http://$node")) : BlockFetcher {
    private val abciApi: ABCIApi = ABCIApi(uri.toString())
    private val infoApi: InfoApi = InfoApi(uri.toString())
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Fetch the current height of the chain.
     */
    override suspend fun getCurrentHeight(): Long =
        abciApi.abciInfo().result?.response?.lastBlockHeight
            ?: throw BlockFetchException("failed to fetch current height")

    /**
     * Fetch block data by height.
     * @param height The height to fetch block data for.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getBlock(height: Long): BlockData {
        log.trace("getBlock($height)")
        val block = infoApi.block(height).result?.block
            ?: throw BlockFetchException("failed to fetch height:$height")
        log.trace("getBlock($height) complete")

        log.trace("get block result($height)")
        val blockResult = infoApi.blockResults(height).result
        log.trace("get block result($height) complete")
        return BlockData(block, blockResult)
    }
}