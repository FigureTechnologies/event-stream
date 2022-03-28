package io.provenance.eventstream.stream

import com.squareup.moshi.Moshi
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.defaultDecoderEngine
import io.provenance.eventstream.defaultEventStreamService
import io.provenance.eventstream.defaultMoshi
import io.provenance.eventstream.defaultOkHttpClient
import io.provenance.eventstream.requireType
import io.provenance.eventstream.stream.rpc.request.Subscribe
import io.provenance.eventstream.stream.rpc.response.MessageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import mu.KotlinLogging
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

/**
 * Decode a [Message] into a [MessageType] using the provided decoder engine.
 *
 * @param engine The [DecoderEngine] instance to use to convert the payload into a MessageType.
 */
fun messageDecoder(
    engine: DecoderEngine
): (Message) -> MessageType {
    val decoder = MessageType.Decoder(engine)
    return { message ->
        requireType<Message.Text>(message) { "invalid type:${message::class.simpleName}" }.let {
            decoder.decode(it.value)
        }
    }
}

/**
 * Decode the flow of [Message] into a flow of [MessageType]
 */
fun Flow<Message>.decodeMessages(
    moshi: Moshi = defaultMoshi(),
    engine: DecoderEngine = defaultDecoderEngine(moshi),
    decoder: (Message) -> MessageType = messageDecoder(engine),
): Flow<MessageType> =
    map { decoder(it) }.onEach {
        if (it is MessageType.Panic) {
            throw CancellationException("RPC endpoint panic: ${it.error}")
        }
    }

/**
 * Creates a new websocket client to listen to events and messages from a tendermint node.
 *
 * @param address The uri of the websocket rpc endpoint to connect to.
 * @param okHttpClient The http client to use to connect to the rpc endpoint with.
 * @param ess The event stream service instance to use to receive events.
 * @param subscription The tendermint websocket subscription to connect with.
 * @return A [Flow] of websocket Messages that can be processed downstream.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun nodeWebSocketClient(
    address: String,
    okHttpClient: OkHttpClient = defaultOkHttpClient(),
    ess: EventStreamService = defaultEventStreamService(address, okHttpClient, 1.seconds),
    subscription: String = "tm.event='NewBlock'",
): Flow<Message> = channelFlow {
    val log = KotlinLogging.logger {}

    invokeOnClose {
        try { ess.stopListening() } catch (e: Throwable) { /* Ignored */ }
    }
    ess.startListening()

    // Toggle the Lifecycle register start state:
    for (event in ess.observeWebSocketEvent()) {
        when (event) {
            is WebSocket.Event.OnConnectionOpened<*> -> {
                log.info("live::connection established, initializing subscription:$subscription")
                ess.subscribe(Subscribe(subscription))
            }

            is WebSocket.Event.OnMessageReceived -> {
                send(event.message)
            }

            is WebSocket.Event.OnConnectionClosing -> {
                close(CancellationException("live::connection closed"))
            }

            is WebSocket.Event.OnConnectionFailed -> {
                throw CancellationException("live::connection failed", event.throwable)
            }

            else -> {
                throw CancellationException("live::unexpected event:$event")
            }
        }
    }
}
