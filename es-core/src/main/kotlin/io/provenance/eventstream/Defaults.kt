package io.provenance.eventstream

import com.tinder.scarlet.MessageAdapter
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.retry.BackoffStrategy
import com.tinder.scarlet.retry.ExponentialWithJitterBackoffStrategy
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import io.provenance.eventstream.stream.WebSocketChannel
import io.provenance.eventstream.stream.flows.DEFAULT_THROTTLE_PERIOD
import kotlinx.coroutines.CancellationException
import mu.KotlinLogging
import java.lang.reflect.Type
import kotlin.time.Duration

/**
 * Adapter type wrapper for [WebSocket.Factory] lambdas.
 */
typealias WsAdapter = () -> WebSocket

/**
 * Adapter method to convert lambda into [WebSocket.Factory]
 */
fun WsAdapter.toScarletFactory(): WebSocket.Factory {
    return object : WebSocket.Factory {
        override fun create(): WebSocket = invoke()
    }
}

/**
 * Adapter type wrapper for [MessageAdapter.Factory] lambdas.
 */
typealias WsDecoderAdapter = (type: Type, annotations: Array<Annotation>) -> MessageAdapter<*>

/**
 * Adapter method to convert lambda into [MessageAdapter.Factory]
 */
fun WsDecoderAdapter.toScarletFactory(): MessageAdapter.Factory {
    return object : MessageAdapter.Factory {
        override fun create(type: Type, annotations: Array<Annotation>): MessageAdapter<*> = invoke(type, annotations)
    }
}

/**
 * Create a sane [BackoffStrategy] that will bail after a maximum number of retries.
 *
 * @param maxRetries The maximum number of retries to attempt (default: -1 - never gonna give it up).
 * @param fallbackStrategy The fallback [BackoffStrategy] to reference if maxRetries is not exceeded.
 */
fun defaultBackoffStrategy(
    maxRetries: Int = -1,
    fallbackStrategy: BackoffStrategy = ExponentialWithJitterBackoffStrategy(1000L, 10000L)
): BackoffStrategy {
    return object : BackoffStrategy {
        val log = KotlinLogging.logger {}
        override fun backoffDurationMillisAt(retryCount: Int): Long {
            if (maxRetries != -1 && retryCount > maxRetries) {
                throw CancellationException("max retry count of $maxRetries exceeded")
            }

            return fallbackStrategy.backoffDurationMillisAt(retryCount).also {
                log.info { "backing off ${it}ms before next connection attempt" }
            }
        }
    }
}

/**
 * Create a [WebSocketChannel] instance via [Scarlet].
 *
 * @param wsFactory The [WsAdapter] used to create [WebSocket] used within [Scarlet] to connect to a host.
 * @param msgAdapterFactory The [WsDecoderAdapter] to use within the web socket to convert into [com.tinder.scarlet.Message].
 * @param throttle The rate to throttle within [Scarlet] for interval polling.
 * @param lifecycle The [LifecycleRegistry] to use for websocket spin-up and tear-down.
 * @return The [WebSocketChannel] instance.
 */
fun defaultWebSocketChannel(
    wsFactory: WsAdapter,
    msgAdapterFactory: WsDecoderAdapter,
    throttle: Duration = DEFAULT_THROTTLE_PERIOD,
    lifecycle: LifecycleRegistry = defaultLifecycle(throttle),
    backoffStrategy: BackoffStrategy = defaultBackoffStrategy(),
): WebSocketChannel {
    val scarlet = Scarlet.Builder()
        .backoffStrategy(backoffStrategy)
        .webSocketFactory(wsFactory.toScarletFactory())
        .addMessageAdapterFactory(msgAdapterFactory.toScarletFactory())
        .addStreamAdapterFactory(CoroutinesStreamAdapterFactory())
        .lifecycle(lifecycle)
        .build()
    return scarlet.create(WebSocketChannel::class.java)
}

/**
 * Create a [LifecycleRegistry] used to manage websocket spin-up and tear-down within [Scarlet].
 */
fun defaultLifecycle(throttle: Duration = DEFAULT_THROTTLE_PERIOD): LifecycleRegistry =
    LifecycleRegistry(throttle.inWholeMilliseconds)
