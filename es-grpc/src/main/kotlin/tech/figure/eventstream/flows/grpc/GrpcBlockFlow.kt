package tech.figure.eventstream.flows.grpc

import cosmos.tx.v1beta1.getBlockWithTxsRequest
import io.provenance.client.grpc.PbClient
import io.provenance.client.protobuf.extensions.getBlockAtHeight
import io.provenance.client.protobuf.extensions.getCurrentBlockHeight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tech.figure.eventstream.common.flows.contiguous
import tech.figure.eventstream.common.flows.pollingFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val DEFAULT_POLL_INTERVAL = 2.seconds

/**
 * Create a generic grpc based flow for fetches.
 *
 * @param pbClient The Provenance Blockchain GRPC client.
 * @param fromHeight The starting height for the flow.
 * @param pollInterval The poll interval to check for new heights.
 * @param fetcher Fetch a [T] using [pbClient] for a certain height.
 */
internal fun <T> grpcFlow(
    pbClient: PbClient,
    fromHeight: Long? = null,
    pollInterval: Duration = DEFAULT_POLL_INTERVAL,
    fetcher: PbClient.(Long) -> T,
) : Flow<T> {
    return pollingFlow(pollInterval) { pbClient.tendermintService.getCurrentBlockHeight() }
        .distinctUntilChanged()
        .contiguous(current = fromHeight, fallback = { it.asFlow() }) { it }
        .map { pbClient.fetcher(it) }
}

/**
 * Create a block-and-transactions flow with grpc based fetches.
 *
 * @param pbClient The Provenance Blockchain GRPC client.
 * @param fromHeight The starting height for the flow.
 * @param pollInterval The poll interval to check for new heights.
 */
fun grpcBlockWithTxsFlow(
    pbClient: PbClient,
    fromHeight: Long? = null,
    pollInterval: Duration = DEFAULT_POLL_INTERVAL,
) = grpcFlow(pbClient, fromHeight, pollInterval) {
    cosmosService.getBlockWithTxs(getBlockWithTxsRequest { this.height = height })
}

/**
 * Create a block flow with grpc based fetches.
 *
 * @param pbClient The Provenance Blockchain GRPC client.
 * @param fromHeight The starting height for the flow.
 * @param pollInterval The poll interval to check for new heights.
 */
fun grpcBlockFlow(
    pbClient: PbClient,
    fromHeight: Long? = null,
    pollInterval: Duration = DEFAULT_POLL_INTERVAL,
) = grpcFlow(pbClient, fromHeight, pollInterval) {
    tendermintService.getBlockAtHeight(it)
}