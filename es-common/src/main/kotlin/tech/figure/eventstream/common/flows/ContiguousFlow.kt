package tech.figure.eventstream.common.flows

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transform

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
fun <T> Flow<T>.contiguous(current: Long? = null, chunkSize: Int = 10, fallback: suspend (ids: List<Long>) -> Flow<T>, indexer: (T) -> Long): Flow<T> {
    var currentHeight = current
    return transform { item ->
        val index = indexer(item)
        if (currentHeight != null && currentHeight!!.inc() < index) {
            // Uh-oh! Found a gap. Fill it in chunkSize items at a time. Don't use fallback for current item.
            ((currentHeight!!.inc()) until index).toList().chunked(chunkSize).map {
                emitAll(fallback(it))
            }
        }
        currentHeight = index
        emit(item)
    }
}
