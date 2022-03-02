package io.provenance.eventstream.stream.clients

import cosmos.base.tendermint.v1beta1.Query
import cosmos.base.tendermint.v1beta1.ServiceGrpc
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.ServiceGrpc as v1ServiceGrpc
import io.grpc.ManagedChannelBuilder
import io.provenance.attribute.v1.QueryAttributeRequest
import io.provenance.client.grpc.GasEstimationMethod
import io.provenance.client.grpc.PbClient
//import io.provenance.client.grpc.GasEstimationMethod
import io.provenance.eventstream.stream.TendermintServiceClient
import io.provenance.eventstream.stream.apis.ABCIApi
import io.provenance.eventstream.stream.apis.InfoApi
import io.provenance.eventstream.stream.models.*
import io.provenance.marker.v1.QueryDenomMetadataRequest
import io.provenance.metadata.v1.QueryGrpc
import io.provenance.metadata.v1.RecordsAllRequest
//import io.provenance.client.grpc.PbClient
import io.provenance.metadata.v1.RecordsRequest
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
class TendermintServiceOpenApiClient(rpcUrlBase: URI) : TendermintServiceClient {
    private val abciApi = ABCIApplicationGrpc.newFutureStub(
        ManagedChannelBuilder
            .forAddress(rpcUrlBase.host, rpcUrlBase.port)
            .useTransportSecurity()
            .build()
    )

    private val serviceApi = ServiceGrpc.newFutureStub(
        ManagedChannelBuilder
            .forAddress(rpcUrlBase.host, 9090)
            .usePlaintext()
            .build()
    )
    private val infoApi = let {
        InfoApi(rpcUrlBase.scheme + "://" + rpcUrlBase.host + ":" + rpcUrlBase.port)
    }


    override suspend fun abciInfo(): Types.ResponseInfo = abciApi.info(Types.RequestInfo.getDefaultInstance()).get()

    override suspend fun block(height: Long?): BlockOuterClass.Block =
    serviceApi.getBlockByHeight(Query.GetBlockByHeightRequest.getDefaultInstance().toBuilder().setHeight(height!!).build()).get().block

    override suspend fun blockResults(height: Long?): BlockResultsResponse = infoApi.blockResults(height)

    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse =
        infoApi.blockchain(minHeight, maxHeight)
}
