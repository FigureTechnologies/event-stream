package io.provenance.eventstream.stream.flows

import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.eventstream.decoder.DecoderAdapter
import io.provenance.eventstream.defaultLifecycle
import io.provenance.eventstream.defaultWebSocketChannel
import io.provenance.eventstream.net.NetAdapter
import io.provenance.eventstream.stream.LiveMetaDataStream
import io.provenance.eventstream.stream.WebSocketChannel
import io.provenance.eventstream.stream.WebSocketService
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockHeader
import io.provenance.eventstream.stream.models.BlockMeta
import io.provenance.eventstream.stream.rpc.request.Subscribe
import io.provenance.eventstream.stream.rpc.response.MessageType
import io.provenance.eventstream.stream.withLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

/**
 * Convert a [Flow] of type [MessageType.NewBlockHeader] into a flow of [BlockHeader]
 */
fun Flow<MessageType.NewBlockHeader>.mapLiveHeaderData(): Flow<BlockHeader> = map { it.header.data.value!!.header!! }

/**
 * Convert a [Flow] of type [MessageType.NewBlock] into a [Flow] of [Block].
 *
 * Mimic the behavior of the [LiveMetaDataStream] using [nodeEventStream] as a source.
 */
fun Flow<MessageType.NewBlock>.mapLiveBlockData(): Flow<Block> =
    map { it.block.data.value.block }

/**
 * Create a [Flow] of historical [BlockMeta] from a node.
 *
 * Convenience wrapper around [nodeEventStream] and [mapLiveHeaderData]
 *
 * @param netAdapter The [NetAdapter] to use to connect to the node.
 * @param decoderAdapter The [DecoderAdapter] to use to convert from json.
 * @param throttle The web socket throttle duration.
 * @param lifecycle The [LifecycleRegistry] instance used to manage startup and shutdown.
 * @param channel The [WebSocketChannel] used to receive incoming websocket events.
 * @param wss The [WebSocketService] used to manage the channel.
 */
fun liveMetadataFlow(
    netAdapter: NetAdapter,
    decoderAdapter: DecoderAdapter,
    throttle: Duration = DEFAULT_THROTTLE_PERIOD,
    lifecycle: LifecycleRegistry = defaultLifecycle(throttle),
    channel: WebSocketChannel = defaultWebSocketChannel(netAdapter.wsAdapter, decoderAdapter.wsDecoder, throttle, lifecycle),
    wss: WebSocketService = channel.withLifecycle(lifecycle),
): Flow<BlockHeader> {
    return nodeEventStream<MessageType.NewBlockHeader>(netAdapter, decoderAdapter, throttle, lifecycle, channel, wss)
        .mapLiveHeaderData()
}
/**
 * Create an event stream subscription to a node.
 *
 * @param netAdapter The [NetAdapter] to use to connect to the node.
 * @param decoderAdapter The [DecoderAdapter] to use to convert from json.
 * @param throttle The web socket throttle duration.
 * @param lifecycle The [LifecycleRegistry] instance used to manage startup and shutdown.
 * @param channel The [WebSocketChannel] used to receive incoming websocket events.
 * @param wss The [WebSocketService] used to manage the channel.
 */
inline fun <reified T : MessageType> nodeEventStream(
    netAdapter: NetAdapter,
    decoderAdapter: DecoderAdapter,
    throttle: Duration = DEFAULT_THROTTLE_PERIOD,
    lifecycle: LifecycleRegistry = defaultLifecycle(throttle),
    channel: WebSocketChannel = defaultWebSocketChannel(netAdapter.wsAdapter, decoderAdapter.wsDecoder, throttle, lifecycle),
    wss: WebSocketService = channel.withLifecycle(lifecycle),
): Flow<T> {
    // Only supported NewBlock and NewBlockHeader right now.
    require(T::class == MessageType.NewBlock::class || T::class == MessageType.NewBlockHeader::class) {
        "unsupported MessageType.${T::class.simpleName}"
    }

    val subscription = T::class.simpleName
    val sub = Subscribe("tm.event='$subscription'")
    return webSocketClient(sub, netAdapter, decoderAdapter, throttle, lifecycle, channel, wss)
        .decodeMessages(decoder = decoderAdapter.jsonDecoder)
}
