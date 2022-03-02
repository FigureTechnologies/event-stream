package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.ABCIInfoResponse
import io.provenance.eventstream.stream.models.BlockResponse
import io.provenance.eventstream.stream.models.BlockResultsResponse
import io.provenance.eventstream.stream.models.BlockchainResponse
import tendermint.abci.Types
import tendermint.types.BlockOuterClass

/**
 * A client designed to interact with the Tendermint RPC API.
 */
interface TendermintServiceClient {
    suspend fun abciInfo(): Types.ResponseInfo
    suspend fun block(height: Long?): BlockOuterClass.Block
    suspend fun blockResults(height: Long?): BlockResultsResponse
    suspend fun blockchain(minHeight: Long?, maxHeight: Long?): BlockchainResponse
}
