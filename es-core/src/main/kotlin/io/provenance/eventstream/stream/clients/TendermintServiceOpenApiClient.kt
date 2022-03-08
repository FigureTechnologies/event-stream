package io.provenance.eventstream.stream.clients

import cosmos.base.tendermint.v1beta1.Query
import cosmos.base.tendermint.v1beta1.ServiceGrpc
import io.grpc.ManagedChannelBuilder
import io.provenance.client.grpc.GasEstimationMethod
import io.provenance.client.grpc.PbClient
import io.provenance.eventstream.stream.TendermintServiceClient
import io.provenance.eventstream.stream.apis.InfoApi
import io.provenance.eventstream.stream.models.BlockResultsResponse
import io.provenance.eventstream.stream.models.BlockchainResponse
import tendermint.abci.ABCIApplicationGrpc
import tendermint.abci.Types
import tendermint.types.BlockOuterClass
import java.net.URI

/**
 * An OpenAPI generated client designed to interact with the Tendermint RPC API.
 *
 * All requests and responses are HTTP+JSON.
 *
 * @param rpcUrlBase The base URL of the Tendermint RPC API to use when making requests.
 */
class TendermintServiceOpenApiClient(rpcUrlBase: URI, chainId: String) : TendermintServiceClient {
    private val pbClient = PbClient(
        chainId = chainId,
        channelUri = URI("http://localhost:9090"),
        gasEstimationMethod = GasEstimationMethod.MSG_FEE_CALCULATION
    )

    private val abciApi = ABCIApplicationGrpc.newFutureStub(
        ManagedChannelBuilder
            .forAddress(rpcUrlBase.host, rpcUrlBase.port)
            .useTransportSecurity()
            .build()
    )

    private val serviceApi = let {
        var port = rpcUrlBase.port
        if (rpcUrlBase.host in mutableListOf("localhost", "127.0.0.1", "0.0.0.0")) {
            port = 9090
        }

        ServiceGrpc.newFutureStub(
            ManagedChannelBuilder
                .forAddress(rpcUrlBase.host, port)
                .usePlaintext()
                .build()
        )
    }

    private val infoApi = let {
        InfoApi(rpcUrlBase.scheme + "://" + rpcUrlBase.host + ":" + rpcUrlBase.port)
    }

    override suspend fun abciInfo(): Types.ResponseInfo = abciApi.info(Types.RequestInfo.getDefaultInstance()).get()

    override suspend fun block(height: Long?): BlockOuterClass.Block =
        pbClient.tendermintService.getBlockByHeight(
            Query.GetBlockByHeightRequest.newBuilder().setHeight(height!!).build()
        ).block

    override suspend fun blockResults(height: Long?): BlockResultsResponse = infoApi.blockResults(height)

    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse =
        infoApi.blockchain(minHeight, maxHeight)
}
