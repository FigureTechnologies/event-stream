package tech.figure.eventstream.stream.flows

import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.retry.BackoffStrategy
import tech.figure.eventstream.stream.models.Block
import tech.figure.eventstream.stream.models.BlockHeader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import tech.figure.eventstream.decoder.DecoderAdapter
import tech.figure.eventstream.defaultBackoffStrategy
import tech.figure.eventstream.defaultLifecycle
import tech.figure.eventstream.defaultWebSocketChannel
import tech.figure.eventstream.net.NetAdapter
import tech.figure.eventstream.stream.NewBlockResult
import tech.figure.eventstream.stream.WebSocketChannel
import tech.figure.eventstream.stream.WebSocketService
import tech.figure.eventstream.stream.clients.BlockData
import tech.figure.eventstream.stream.rpc.response.MessageType
import tech.figure.eventstream.stream.withLifecycle
import kotlin.time.Duration

/**
 * Convert a [Flow] of type [MessageType.NewBlockHeader] into a flow of [BlockHeader]
 */
fun Flow<MessageType.NewBlock>.mapLiveBlockResult(): Flow<NewBlockResult> = map { it.block }

/**
 * Convert a [Flow] of type [MessageType.NewBlock] into a [Flow] of [Block].
 *
 * Mimic the behavior of the [LiveMetaDataStream] using [nodeEventStream] as a source.
 */
fun Flow<MessageType.NewBlock>.mapLiveBlock(): Flow<Block> =
    map { it.block.data.value.block }

/**
 * Create a [Flow] of live [BlockData] from a node.
 *
 * Convenience wrapper around [nodeEventStream] and [mapBlockData]
 *
 * @param netAdapter The [NetAdapter] to use to connect to the node.
 * @param decoderAdapter The [DecoderAdapter] to use to convert from json.
 * @param throttle The web socket throttle duration.
 * @param lifecycle The [LifecycleRegistry] instance used to manage startup and shutdown.
 * @param channel The [WebSocketChannel] used to receive incoming websocket events.
 * @param wss The [WebSocketService] used to manage the channel.
 */
fun wsBlockDataFlow(
    netAdapter: NetAdapter,
    decoderAdapter: DecoderAdapter,
    throttle: Duration = DEFAULT_THROTTLE_PERIOD,
    backoffStrategy: BackoffStrategy = defaultBackoffStrategy(),
    lifecycle: LifecycleRegistry = defaultLifecycle(throttle),
    channel: WebSocketChannel = defaultWebSocketChannel(netAdapter.wsAdapter, decoderAdapter.wsDecoder, throttle, lifecycle),
    wss: WebSocketService = channel.withLifecycle(lifecycle),
): Flow<BlockData> {
    return nodeEventStream<MessageType.NewBlock>(netAdapter, decoderAdapter, throttle, lifecycle, backoffStrategy, channel, wss)
        .mapBlockData(netAdapter)
        .contiguous(netAdapter.rpcAdapter::getBlocks) { it.height }
}

/**
 * Convert a list of heights into a [Flow] of [BlockData].
 *
 * @param netAdapter The [NetAdapter] to use to interface with the node rpc.
 * @return The [Flow] of [BlockData]
 */
fun Flow<MessageType.NewBlock>.mapBlockData(netAdapter: NetAdapter): Flow<BlockData> {
    val fetcher = netAdapter.rpcAdapter
    return map { fetcher.getBlock(it.block.data.value.block.header!!.height) }
}

/**
 * Generate contiguous runs of data, aka: fill in the gaps.
 *
 * @param fallback Method to pull any missing T's by the id provided.
 * @param indexer Method to pull id from the next T.
 *
 * ```kotlin
 *     listOf(1, 5).asFlow().contiguous({ it }) { it }.toList() == listOf(1, 2, 3, 4, 5)
 * ```
 */
internal fun <T> Flow<T>.contiguous(fallback: suspend (ids: List<Long>) -> Flow<T>, indexer: (T) -> Long): Flow<T> {
    var current: Long? = null
    return transform { item ->
        val index = indexer(item)
        if (current != null && current!!.inc() < index) {
            // Uh-oh! Found a gap. Fill it in. Don't use fallback for current item.
            val missingIds = ((current!!.inc()) until index).toList()
            emitAll(fallback(missingIds))
        }
        current = index
        emit(item)
    }
}
