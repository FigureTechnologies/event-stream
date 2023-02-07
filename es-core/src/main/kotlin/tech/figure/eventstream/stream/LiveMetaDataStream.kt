package tech.figure.eventstream.stream

import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import tech.figure.eventstream.stream.models.Block
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import mu.KotlinLogging
import tech.figure.eventstream.adapter.json.decoder.DecoderEngine
import tech.figure.eventstream.stream.rpc.request.Subscribe
import tech.figure.eventstream.stream.rpc.response.MessageType

/**
 * Create a [Flow] of [Block] from a [WebSocketService].
 *
 * @param eventStreamService The [WebSocketService] web socket instance to receive events from.
 * @param decoder The [DecoderEngine] to use to marshal the [Message] to [MessageType].
 */
class LiveMetaDataStream(
    private val eventStreamService: WebSocketService,
    private val decoder: DecoderEngine,
) {

    private val log = KotlinLogging.logger { }

    /**
     *
     * Constructs a Flow of newly minted blocks and associated events as the blocks are added to the chain.
     *
     * @return A Flow of newly minted blocks and associated events
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun streamBlocks(): Flow<Block> {

        // Toggle the Lifecycle register start state:
        eventStreamService.start()

        return channelFlow {
            for (event in eventStreamService.observeWebSocketEvent()) {
                when (event) {
                    is WebSocket.Event.OnConnectionOpened<*> -> {
                        log.info("streamLiveBlocks::received OnConnectionOpened event")
                        log.info("streamLiveBlocks::initializing subscription for tm.event='NewBlock'")
                        eventStreamService.subscribe(Subscribe("tm.event='NewBlock'"))
                    }
                    is WebSocket.Event.OnMessageReceived -> when (event.message) {
                        is Message.Text -> {
                            val message = event.message as Message.Text
                            when (val type = responseMessageDecoder.decode(message.value)) {
                                is MessageType.Empty -> log.info("received empty ACK message => ${message.value}")
                                is MessageType.NewBlock -> {
                                    val block = type.block.data.value.block
                                    log.info("live::received NewBlock message: #${block.header?.height}")
                                    send(block)
                                }
                                is MessageType.Error -> log.error("upstream error from RPC endpoint: ${type.error}")
                                is MessageType.Panic -> {
                                    log.error("upstream panic from RPC endpoint: ${type.error}")
                                    throw CancellationException("RPC endpoint panic: ${type.error}")
                                }
                                is MessageType.Unknown -> log.info("unknown message type; skipping message => ${message.value}")
                            }
                        }
                        is Message.Bytes -> {
                            // ignore; binary payloads not supported:
                            log.warn("live::binary message payload not supported")
                        }
                    }
                    is WebSocket.Event.OnConnectionFailed -> throw event.throwable
                    else -> throw Throwable("live::unexpected event type: $event")
                }
            }
        }
    }

    /**
     * A decoder for Tendermint RPC API messages.
     */
    private val responseMessageDecoder: MessageType.Decoder = MessageType.Decoder(decoder)
}
