package tech.figure.eventstream.mocks

import tech.figure.eventstream.stream.models.BlockResponse
import tech.figure.eventstream.stream.models.BlockResultsResponse
import tech.figure.eventstream.stream.models.BlockchainResponse
import tech.figure.eventstream.stream.clients.TendermintServiceClient
import tech.figure.eventstream.stream.models.ABCIInfoResponse

class MockTendermintServiceClient(mocker: ServiceMock) : TendermintServiceClient, ServiceMock by mocker {

    override suspend fun abciInfo() =
        respondWith<ABCIInfoResponse>("abciInfo")

    override suspend fun block(height: Long?) =
        respondWith<BlockResponse>("block", height)

    override suspend fun blockResults(height: Long?) =
        respondWith<BlockResultsResponse>("blockResults", height)

    override suspend fun blockchain(minHeight: Long?, maxHeight: Long?) =
        respondWith<BlockchainResponse>("blockchain", minHeight, maxHeight)
}
