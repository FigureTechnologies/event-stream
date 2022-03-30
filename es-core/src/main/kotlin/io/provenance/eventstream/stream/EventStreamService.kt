package io.provenance.eventstream.stream

import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.ws.Receive
import com.tinder.scarlet.ws.Send
import io.provenance.eventstream.stream.rpc.request.Subscribe
import kotlinx.coroutines.channels.ReceiveChannel
import mu.KotlinLogging

/**
 * Used by the Scarlet library to instantiate an implementation that provides access to a
 * `ReceiveChannel<WebSocket.Event>` that can be used to listen for web socket events.
 */
interface WebSocketChannel {
    /**
     * Receive events from the web socket.
     */
    @Receive
    fun observeWebSocketEvent(): ReceiveChannel<WebSocket.Event>

    /**
     * Subscribe to the web socket events.
     */
    @Send
    fun subscribe(subscribe: Subscribe)
}

fun WebSocketChannel.withLifecycle(lifecycle: LifecycleRegistry): WebSocketService =
    object : WebSocketService, WebSocketChannel by this, WebSocketLifecycle by webSocketLifecycle(lifecycle) {}

/**
 *
 */
interface WebSocketLifecycle {
    /**
     * Start the web socket stream.
     */
    fun start()

    /**
     * Stop the web socket stream.
     */
    fun stop()
}

/**
 * Create a websocket lifecycle tied to scarlet lifecycle registry.
 *
 * @param lifecycle The lifecycle responsible for starting and stopping the underlying websocket event stream.
 */
fun webSocketLifecycle(lifecycle: LifecycleRegistry): WebSocketLifecycle = object : WebSocketLifecycle {
    private val log = KotlinLogging.logger {}

    /**
     * Allows the provided event stream to start receiving events.
     *
     * Note: this must be called prior to any receiving any events on the RPC stream.
     */
    override fun start() {
        log.info { "start()" }
        lifecycle.onNext(Lifecycle.State.Started)
    }

    /**
     * Stops the provided event stream from receiving events.
     */
    override fun stop() {
        log.info { "stop()" }
        lifecycle.onNext(Lifecycle.State.Stopped.AndAborted)
    }
}

/**
 *
 */
interface WebSocketService : WebSocketChannel, WebSocketLifecycle
