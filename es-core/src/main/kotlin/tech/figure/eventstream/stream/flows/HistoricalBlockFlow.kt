package tech.figure.eventstream.stream.flows

import tech.figure.eventstream.net.NetAdapter
import tech.figure.eventstream.stream.clients.BlockData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Create a [Flow] of historical [BlockData] from a node.
 *
 * @param netAdapter The [NetAdapter] to use to interface with the node rpc.
 * @param from The `from` height to begin the stream with.
 * @param to The `to` height to fetch until. If omitted, use the current height.
 * @param concurrency The coroutine concurrency setting for async parallel fetches.
 * @param context The coroutine context to execute the async parallel fetches.
 * @return The [Flow] of [BlockData]
 */
fun historicalBlockDataFlow(
    netAdapter: NetAdapter,
    from: Long = 1,
    to: Long? = null,
    concurrency: Int = DEFAULT_CONCURRENCY,
    context: CoroutineContext = EmptyCoroutineContext,
    currentHeight: Long? = null,
): Flow<BlockData> = flow {
    suspend fun currentHeight() =
        netAdapter.rpcAdapter.getCurrentHeight() ?: throw RuntimeException("cannot fetch current height")

    val realTo = currentHeight ?: (to ?: currentHeight())
    require(from <= realTo) { "from:$from must be less than to:$realTo" }

    emitAll((from..realTo).toList().toBlockData(netAdapter, concurrency, context))
}

/**
 * Convert a list of heights into a [Flow] of [BlockData].
 *
 * @param netAdapter The [NetAdapter] to use to interface with the node rpc.
 * @param concurrency The coroutine concurrency setting for async parallel fetches.
 * @param context The coroutine context to execute the async parallel fetches.
 * @return The [Flow] of [BlockData]
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun List<Long>.toBlockData(
    netAdapter: NetAdapter,
    concurrency: Int = DEFAULT_CONCURRENCY,
    context: CoroutineContext = EmptyCoroutineContext,
): Flow<BlockData> {
    val list = this
    val fetcher = netAdapter.rpcAdapter
    return channelFlow {
        fetcher.getBlocks(list, concurrency, context).collect {
            send(it)
        }
    }
}
