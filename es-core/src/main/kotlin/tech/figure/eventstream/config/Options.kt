package tech.figure.eventstream.config

import tech.figure.eventstream.stream.EventStream
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY

data class Options(
    val concurrency: Int,
    val batchSize: Int,
    val fromHeight: Long?,
    val toHeight: Long?,
    val skipIfEmpty: Boolean,
    val blockEventPredicate: ((event: String) -> Boolean)?,
    val txEventPredicate: ((event: String) -> Boolean)?,
    val ordered: Boolean = true,
) {
    companion object {
        val DEFAULT: Options = Builder().build()
    }

    fun withConcurrency(concurrency: Int) = this.copy(concurrency = concurrency)

    fun withBatchSize(size: Int) = this.copy(batchSize = size)

    fun withFromHeight(height: Long?) = this.copy(fromHeight = height)

    fun withToHeight(height: Long?) = this.copy(toHeight = height)

    fun withSkipIfEmpty(value: Boolean) = this.copy(skipIfEmpty = value)

    fun withBlockEventPredicate(predicate: ((event: String) -> Boolean)?) =
        this.copy(blockEventPredicate = predicate)

    fun withTxEventPredicate(predicate: ((event: String) -> Boolean)?) = this.copy(txEventPredicate = predicate)

    class Builder {
        private var concurrency: Int = DEFAULT_CONCURRENCY
        private var batchSize: Int = EventStream.DEFAULT_BATCH_SIZE
        private var fromHeight: Long? = null
        private var toHeight: Long? = null
        private var skipIfEmpty: Boolean = true
        private var blockEventPredicate: ((event: String) -> Boolean)? = null
        private var txEventPredicate: ((event: String) -> Boolean)? = null
        private var ordered: Boolean = true

        /**
         * Sets the concurrency level when merging disparate streams of block data.
         *
         * @param level The concurrency level.
         */
        fun concurrency(level: Int) = apply { concurrency = level }

        /**
         * Sets the maximum number of blocks that will be fetched and processed concurrently.
         *
         * @param size The batch size.
         */
        fun batchSize(size: Int) = apply { batchSize = size }

        /**
         * Sets the lowest height to fetch historical blocks from.
         *
         * If no minimum height is provided, only live blocks will be streamed.
         *
         * @param height The minimum height to fetch blocks from.
         */
        fun fromHeight(height: Long?) = apply { fromHeight = height }
        fun fromHeight(height: Long) = apply { fromHeight = height }

        /**
         * Sets the highest height to fetch historical blocks to. If no maximum height is provided, blocks will
         * be fetched up to the latest height, as resulted by the ABCIInfo endpoint.
         *
         * @param height The maximum height to fetch blocks to.
         */
        fun toHeight(height: Long?) = apply { toHeight = height }
        fun toHeight(height: Long) = apply { toHeight = height }

        /**
         * Toggles skipping blocks that contain no transaction data.
         *
         * @param value If true, blocks that contain no transaction data will not be processed.
         */
        fun skipIfEmpty(value: Boolean) = apply { skipIfEmpty = value }

        /**
         * Toggles ordering blocks that as they arrive.
         *
         * @param value If true, incoming blocks will be ordered by height.
         */
        fun ordered(value: Boolean) = apply { ordered = value }

        /**
         * Filter blocks by one or more specific block events (case-insensitive).
         * Only blocks possessing the specified block event(s) will be streamed.
         *
         * @param predicate If evaluates to true will include the given block for processing.
         */
        fun matchBlockEvent(predicate: (event: String) -> Boolean) = apply { blockEventPredicate = predicate }

        /**
         * Filter blocks by one or more specific transaction events (case-insensitive).
         * Only blocks possessing the specified transaction event(s) will be streamed.
         *
         * @param predicate If evaluated to true will include the given block for processing.
         */
        fun matchTxEvent(predicate: (event: String) -> Boolean) = apply { txEventPredicate = predicate }

        /**
         * @return An Options instance used to construct an event stream
         */
        fun build(): Options = Options(
            concurrency = concurrency,
            batchSize = batchSize,
            fromHeight = fromHeight,
            toHeight = toHeight,
            skipIfEmpty = skipIfEmpty,
            blockEventPredicate = blockEventPredicate,
            txEventPredicate = txEventPredicate,
            ordered = ordered,
        )
    }
}
