package io.provenance.eventstream.stream

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY

// Help out the type system.
fun blockStreamCfg(fn: BlockStreamCfg): BlockStreamCfg = { fn() }

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
data class BlockStreamOptions(
    val concurrency: Int = DEFAULT_CONCURRENCY,
    val batchSize: Int = EventStream.DEFAULT_BATCH_SIZE,
    val fromHeight: Long? = null,
    val toHeight: Long? = null,
    val skipEmptyBlocks: Boolean = false,
    val blockEvents: Set<String> = emptySet(),
    val txEvents: Set<String> = emptySet(),
) {
    companion object {
        fun create(vararg options: BlockStreamCfg): BlockStreamOptions {
            return options.fold(BlockStreamOptions()) { acc, fn -> fn(acc) }
        }
    }
}

/**
 * Sets the concurrency level when merging disparate streams of block data.
 *
 * @property level The concurrency level.
 */
fun withConcurrency(concurrency: Int) = blockStreamCfg { copy(concurrency = concurrency) }

/**
 * Sets the maximum number of blocks that will be fetched and processed concurrently.
 *
 * @property size The batch size.
 */
fun withBatchSize(size: Int) = blockStreamCfg { copy(batchSize = size) }

/**
 * Sets the lowest height to fetch historical blocks from.
 *
 * If no minimum height is provided, only live blocks will be streamed.
 *
 * @property height The minimum height to fetch blocks from.
 */
fun withFromHeight(height: Long?) = blockStreamCfg { copy(fromHeight = height) }

/**
 * Sets the highest height to fetch historical blocks to. If no maximum height is provided, blocks will
 * be fetched up to the latest height, as resulted by the ABCIInfo endpoint.
 *
 * @property height The maximum height to fetch blocks to.
 */
fun withToHeight(height: Long?) = blockStreamCfg { copy(toHeight = height) }

/**
 * Toggles skipping blocks that contain no transaction data.
 *
 * @property value If true, blocks that contain no transaction data will not be processed.
 */
fun withSkipEmptyBlocks(value: Boolean?) = blockStreamCfg { if (value == null) this else copy(skipEmptyBlocks = value) }

/**
 * Filter blocks by one or more specific block events (case-insensitive).
 * Only blocks possessing the specified block event(s) will be streamed.
 *
 * @property predicate If evaluates to true will include the given block for processing.
 */
fun withBlockEvents(events: Set<String>) = blockStreamCfg { copy(blockEvents = events) }

/**
 * Filter blocks by one or more specific transaction events (case-insensitive).
 * Only blocks possessing the specified transaction event(s) will be streamed.
 *
 * @property predicate If evaluated to true will include the given block for processing.
 */
fun withTxEvents(events: Set<String>) = blockStreamCfg { copy(txEvents = events) }