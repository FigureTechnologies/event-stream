package io.provenance.eventstream.stream

import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.eventstream.WsAdapter
import io.provenance.eventstream.WsDecoderAdapter
import io.provenance.eventstream.adapter.json.decoder.MessageDecoder
import io.provenance.eventstream.decoder.DecoderAdapter
import io.provenance.eventstream.defaultLifecycle
import io.provenance.eventstream.defaultWebSocketChannel
import io.provenance.eventstream.net.NetAdapter
import io.provenance.eventstream.stream.rpc.request.Subscribe
import io.provenance.eventstream.stream.rpc.response.MessageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import mu.KotlinLogging
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val DEFAULT_THROTTLE_PERIOD = 1.seconds

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
    return webSocketClient(sub, netAdapter.wsAdapter, decoderAdapter.wsDecoder, throttle, lifecycle, channel, wss)
        .decodeMessages(decoder = decoderAdapter.jsonDecoder)
}

/**
 * Decode the flow of [Message] into a flow of [MessageType]
 *
 * @param decoder The decoder function used to decode the [Message]s from the websocket.
 * @return A [Flow] of decoded websocket messages of type [MessageType]
 * @throws [CancellationException] When [MessageType.Panic] is encountered in the source flow.
 */
@Suppress("unchecked_cast")
fun <T : MessageType> Flow<Message>.decodeMessages(decoder: MessageDecoder): Flow<T> =
    map { decoder(it) }.transform {
        val log = KotlinLogging.logger {}
        when (it) {
            is MessageType.Panic ->
                throw CancellationException("RPC endpoint panic: ${it.error}")

            is MessageType.Error -> log.error { "failed to handle ws message:${it.error}" }
            is MessageType.Unknown -> log.info { "unknown message type:${it.type}" }
            is MessageType.Empty -> log.debug { "received empty message type" }

            else -> emit(it as T)
        }
    }

/**
 * Creates a new websocket client to listen to events and messages from a tendermint node.
 *
 * @param subscription The tendermint websocket subscription to connect with.
 * @return A [Flow] of websocket Messages that can be processed downstream.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun webSocketClient(
    subscription: Subscribe,
    wsAdapter: WsAdapter,
    wsDecoderAdapter: WsDecoderAdapter,
    throttle: Duration = DEFAULT_THROTTLE_PERIOD,
    lifecycle: LifecycleRegistry = defaultLifecycle(throttle),
    channel: WebSocketChannel = defaultWebSocketChannel(wsAdapter, wsDecoderAdapter, throttle, lifecycle),
    wss: WebSocketService = channel.withLifecycle(lifecycle),
): Flow<Message> = channelFlow {
    val log = KotlinLogging.logger {}

    invokeOnClose {
        try { wss.stop() } catch (e: Throwable) { /* Ignored */ }
    }

    // Toggle the Lifecycle register start state
    log.debug { "starting web socket client" }
    wss.start()

    log.debug { "listening for web events" }
    for (event in wss.observeWebSocketEvent()) {
        log.trace { "got event: $event" }
        when (event) {
            is WebSocket.Event.OnConnectionOpened<*> -> {
                log.debug { "connection established, initializing subscription:$subscription" }
                wss.subscribe(subscription)
            }

            is WebSocket.Event.OnMessageReceived -> {
                send(event.message)
            }

            is WebSocket.Event.OnConnectionClosing -> {
                close(CancellationException("connection closed"))
            }

            is WebSocket.Event.OnConnectionFailed -> {
                throw CancellationException("connection failed", event.throwable)
            }

            else -> {
                throw CancellationException("unexpected event:$event")
            }
        }
    }
}
