package io.provenance.eventstream.flow.extensions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Cancels the flow upon receipt of a signal from
 *
 * Adapted from https://stackoverflow.com/a/59109105
 *
 * @see https://stackoverflow.com/a/59109105
 *
 * @param signal When an item is received on the channel, the underlying flow will be cancelled.
 */
@OptIn(ExperimentalStdlibApi::class, InternalCoroutinesApi::class, FlowPreview::class)
fun <T> Flow<T>.cancelOnSignal(signal: Channel<Unit>): Flow<T> = flow {
    val outer = this
    try {
        coroutineScope {
            launch {
                signal.receive()
                this@coroutineScope.cancel()
            }

            collect(object : FlowCollector<T> {
                override suspend fun emit(value: T) {
                    outer.emit(value)
                }
            })
        }
    } catch (e: CancellationException) {
        // ignore
    }
}

// Code below is adapted from https://github.com/Kotlin/kotlinx.coroutines/pull/1558

/**
 * Returns a flow of lists each not exceeding the given [size].
 * The last list in the resulting flow may have less elements than the given [size].
 *
 * @param size the number of elements to take in each list, must be positive and can be greater than the number of elements in this flow.
 * @param timeout If an element is not emitted in the specified duration, the buffer will be emitted downstream if
 *  it is non-empty.
 */
@OptIn(FlowPreview::class, ExperimentalTime::class)
fun <T> Flow<T>.chunked(size: Int, timeout: Duration? = null): Flow<List<T>> =
    chunked(size = size, timeout = timeout) { it.toList() }

/**
 * Chunks a flow of elements into flow of lists, each not exceeding the given [size]
 * and applies the given [transform]function to an each.
 *
 * Note that the list passed to the [transform] function is ephemeral and is valid only inside that function.
 * You should not store it or allow it to escape in some way, unless you made a snapshot of it.
 * The last list may have less elements than the given [size].
 *
 * This is more efficient, than using flow.chunked(n).map { ... }
 *
 * @param size the number of elements to take in each list, must be positive and can be greater than the number of elements in this flow.
 * @param timeout If an element is not emitted in the specified duration, the buffer will be emitted downstream if
 *  it is non-empty.
 */
@OptIn(FlowPreview::class, ExperimentalTime::class)
fun <T, R> Flow<T>.chunked(size: Int, timeout: Duration? = null, transform: suspend (List<T>) -> R): Flow<R> {
    require(size > 0) { "Size should be greater than 0, but was $size" }
    return windowed(size = size, step = size, partialWindows = true, timeout = timeout, transform = transform)
}

/**
 * Returns a flow of snapshots of the window of the given [size]
 * sliding along this flow with the given [step], where each
 * snapshot is a list.
 *
 * Several last lists may have less elements than the given [size].
 *
 * Both [size] and [step] must be positive and can be greater than the number of elements in this flow.
 * @param size the number of elements to take in each window
 * @param step the number of elements to move the window forward by on an each step
 * @param partialWindows controls whether or not to keep partial windows in the end if any.
 * @param timeout If an element is not emitted in the specified duration, the buffer will be emitted downstream if
 *  it is non-empty.
 */
@OptIn(FlowPreview::class, ExperimentalTime::class)
fun <T> Flow<T>.windowed(size: Int, step: Int, partialWindows: Boolean, timeout: Duration? = null): Flow<List<T>> =
    windowed(size = size, step = step, partialWindows = partialWindows, timeout = timeout) { it.toList() }

/**
 * Returns a flow of results of applying the given [transform] function to
 * an each list representing a view over the window of the given [size]
 * sliding along this collection with the given [step].
 *
 * Note that the list passed to the [transform] function is ephemeral and is valid only inside that function.
 * You should not store it or allow it to escape in some way, unless you made a snapshot of it.
 * Several last lists may have less elements than the given [size].
 *
 * This is more efficient, than using flow.windowed(...).map { ... }
 *
 * Both [size] and [step] must be positive and can be greater than the number of elements in this collection.
 * @param size the number of elements to take in each window
 * @param step the number of elements to move the window forward by on an each step.
 * @param partialWindows controls whether or not to keep partial windows in the end if any.
 * @param timeout If an element is not emitted in the specified duration, the buffer will be emitted downstream if
 *  it is non-empty.
 */
@OptIn(
    InternalCoroutinesApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalTime::class,
    ExperimentalStdlibApi::class,
    FlowPreview::class,
)
fun <T, R> Flow<T>.windowed(
    size: Int,
    step: Int,
    partialWindows: Boolean,
    timeout: Duration? = null,
    transform: suspend (List<T>) -> R
): Flow<R> {
    require(size > 0 && step > 0) { "Size and step should be greater than 0, but was size: $size, step: $step" }

    // Using a channelFlow as opposed to a flow allows for up to emit elements from a different coroutine context
    // and is necessary to allow for the use of `launch { ... }` below as a watcher for emission activity.
    return channelFlow {

        val buffer: ArrayDeque<T> = ArrayDeque<T>(size)
        val toDrop: Int = min(step, size)
        val toSkip: Int = max(step - size, 0)
        var skipped: Int = toSkip
        var lastEmittedAt: Instant? = null
        val pollInterval = Duration.seconds(2)

        fun updateEmissionTime() {
            lastEmittedAt = Clock.System.now()
        }

        fun elapsedEmissionTime(): Duration? {
            return lastEmittedAt?.let { Clock.System.now() - it }
        }

        // Launch a coroutine that will check the last time an element was emitted. If the elapsed time since an
        // element was collected from the parent flow exceeds `timeout`, the buffer will forcibly be emitted if the
        // buffer is non-empty.
        if (timeout != null) {
            launch {
                while (true) {
                    elapsedEmissionTime()
                        ?.also { elapsed: Duration ->
                            if (elapsed >= timeout && buffer.isNotEmpty()) {
                                send(transform(buffer))
                                updateEmissionTime()
                                repeat(min(toDrop, buffer.size)) {
                                    buffer.removeFirst()
                                }
                            }
                        }
                    delay(pollInterval)
                }
            }
        }

        collect(object : FlowCollector<T> {
            override suspend fun emit(value: T) {
                if (toSkip == skipped) {
                    buffer.addLast(value)
                } else {
                    skipped++
                }

                if (buffer.size == size) {
                    send(transform(buffer))
                    updateEmissionTime()
                    repeat(toDrop) {
                        buffer.removeFirst()
                    }
                    skipped = 0
                }
            }
        })

        while (partialWindows && buffer.isNotEmpty()) {
            send(transform(buffer))
            updateEmissionTime()
            repeat(min(toDrop, buffer.size)) {
                buffer.removeFirst()
            }
        }
    }
}
