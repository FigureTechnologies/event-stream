package tech.figure.eventstream.stream.flows

import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.retry.BackoffStrategy
import kotlinx.coroutines.flow.Flow
import tech.figure.eventstream.decoder.DecoderAdapter
import tech.figure.eventstream.defaultBackoffStrategy
import tech.figure.eventstream.defaultLifecycle
import tech.figure.eventstream.defaultWebSocketChannel
import tech.figure.eventstream.net.NetAdapter
import tech.figure.eventstream.stream.WebSocketChannel
import tech.figure.eventstream.stream.WebSocketService
import tech.figure.eventstream.stream.rpc.request.Subscribe
import tech.figure.eventstream.stream.rpc.response.MessageType
import tech.figure.eventstream.stream.withLifecycle
import kotlin.time.Duration

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
    backoffStrategy: BackoffStrategy = defaultBackoffStrategy(),
    channel: WebSocketChannel = defaultWebSocketChannel(netAdapter.wsAdapter, decoderAdapter.wsDecoder, throttle, lifecycle, backoffStrategy),
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
