package tech.figure.eventstream.stream.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/**
 * Create a polling [Flow].
 *
 * @param pollInterval The interval to wait between calls to [block].
 * @param block Lambda returning the next element to be emitted, or null if nothing is ready.
 */
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <T> pollingFlow(pollInterval: Duration, crossinline block: suspend () -> T?): Flow<T> = channelFlow {
    while (!isClosedForSend) {
        val data = block()
        if (data == null) {
            delay(pollInterval.inWholeMilliseconds)
            continue
        }
        send(data)
    }
}

/**
 *
 */
internal fun <T> pollingDataFlow(
    getHeight: suspend () -> Long,
    pollInterval: Duration,
    from: Long?,
    fetch: suspend (List<Long>) -> List<T>
): Flow<T> = flow {
    var current = from ?: getHeight()
    pollingFlow(pollInterval) {
        val newCurrent = getHeight()
        val diff = newCurrent - current
        val data = when {
            diff >= 0L -> fetch((current..newCurrent).toList())
            else -> null
        }
        current = newCurrent + 1
        return@pollingFlow data
    }.collect { it.forEach { block -> emit(block) } }
}
