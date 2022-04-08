package io.provenance.eventstream.stream.clients

import cosmos.base.abci.v1beta1.Abci
import cosmos.base.tendermint.v1beta1.Query
import io.grpc.ManagedChannelBuilder
import io.provenance.client.grpc.GasEstimationMethod
import io.provenance.client.grpc.PbClient
import io.provenance.client.protobuf.extensions.getCurrentBlockHeight
import io.provenance.eventstream.stream.TendermintServiceClient
import io.provenance.eventstream.stream.apis.InfoApi
import io.provenance.eventstream.stream.models.BlockchainResponse
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
    private val pbClient = let {
        var port = rpcUrlBase.port
        if (rpcUrlBase.host in mutableListOf("localhost", "127.0.0.1", "0.0.0.0")) {
            port = 9090
        }
        val uri = URI(rpcUrlBase.scheme + "://" + rpcUrlBase.host + ":" + port)

        PbClient(
            chainId = "localnet-main",
            channelUri = uri,
            gasEstimationMethod = GasEstimationMethod.MSG_FEE_CALCULATION
        )
    }

    private val blockResultsService = let {
        var port = rpcUrlBase.port
        if (port == -1) {
            port = rpcUrlBase.schemeSpecificPart.toInt()
        }
        var host = rpcUrlBase.host ?: rpcUrlBase.scheme

        cosmos.tx.v1beta1.ServiceGrpc.newBlockingStub(ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build())
    }

    private val infoApi = let {
        InfoApi("http" + "://" + rpcUrlBase.host + ":" + rpcUrlBase.port)
    }

    override suspend fun getHeight(): Long = pbClient.tendermintService.getCurrentBlockHeight()

    override suspend fun block(height: Long?): BlockOuterClass.Block = pbClient.tendermintService.getBlockByHeight(Query.GetBlockByHeightRequest.newBuilder().setHeight(height ?: 1).build()).block

    override suspend fun blockResults(block: BlockOuterClass.Block): io.provenance.eventstream.stream.clients.BlockResultsResponse {
        //TODO getTxsEvent returns what is needed. but requires event which we used to get FROM this request :(
//        blockResultsService.getTxsEvent(ServiceOuterClass.GetTxsEventRequest.newBuilder().setEvents().build()).txResponsesList[0]
        return BlockResultsResponse(mutableListOf(), mutableListOf())
    }

    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse =
        infoApi.blockchain(minHeight, maxHeight)

    override suspend fun blockResults(height: Long?): io.provenance.eventstream.stream.clients.BlockResultsResponse {
        TODO("Unimplemented")
    }
}

data class BlockResultsResponse(val txEvents: List<Abci.StringEvent>, val blockEvents: List<Types.Event>)
