package io.provenance.eventstream.stream.clients

import io.provenance.eventstream.stream.apis.ABCIApi
import io.provenance.eventstream.stream.apis.InfoApi
import io.provenance.eventstream.stream.models.ABCIInfoResponse
import io.provenance.eventstream.stream.models.BlockResponse
import io.provenance.eventstream.stream.models.BlockResultsResponse
import io.provenance.eventstream.stream.models.BlockchainResponse

/**
 * An OpenAPI generated client designed to interact with the Tendermint RPC API.
 *
 * All requests and responses are HTTP+JSON.
 *
 * @property rpcUrlBase The base URL of the Tendermint RPC API to use when making requests.
 */
class TendermintServiceOpenApiClient(rpcUrlBase: String) : TendermintServiceClient {
    private val abciApi = ABCIApi(rpcUrlBase)
    private val infoApi = InfoApi(rpcUrlBase)

    override suspend fun abciInfo(): ABCIInfoResponse = abciApi.abciInfo()

    override suspend fun block(height: Long?): BlockResponse = infoApi.block(height)

    override suspend fun blockResults(height: Long?): BlockResultsResponse = infoApi.blockResults(height)

    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse =
        infoApi.blockchain(minHeight, maxHeight)
}