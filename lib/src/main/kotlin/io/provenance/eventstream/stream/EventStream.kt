package io.provenance.eventstream.stream

import com.squareup.moshi.Moshi
import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.stream.clients.BlockFetcher
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.producers.HistoricalBlockSource
import io.provenance.eventstream.stream.producers.LiveBlockSource
import io.provenance.eventstream.utils.backoff
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import mu.KotlinLogging
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException
import kotlin.time.ExperimentalTime

@OptIn(FlowPreview::class, ExperimentalTime::class)
@ExperimentalCoroutinesApi
class EventStream(
    private val eventStreamService: EventStreamService,
    private val blockFetcher: BlockFetcher,
    private val tendermintServiceClient: TendermintServiceClient,
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val options: Options = Options.DEFAULT
) : BlockSource {
    companion object {
        /**
         * The default number of blocks that will be contained in a batch.
         */
        const val DEFAULT_BATCH_SIZE = 8
    }

    data class Options(
        val concurrency: Int,
        val batchSize: Int,
        val fromHeight: Long?,
        val toHeight: Long?,
        val skipIfEmpty: Boolean,
        val blockEventPredicate: ((event: String) -> Boolean)?,
        val txEventPredicate: ((event: String) -> Boolean)?
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
            private var batchSize: Int = DEFAULT_BATCH_SIZE
            private var fromHeight: Long? = null
            private var toHeight: Long? = null
            private var skipIfEmpty: Boolean = true
            private var blockEventPredicate: ((event: String) -> Boolean)? = null
            private var txEventPredicate: ((event: String) -> Boolean)? = null

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
                txEventPredicate = txEventPredicate
            )
        }
    }

    private val log = KotlinLogging.logger { }

    /**
     * A serializer function that converts a [StreamBlock] instance to a JSON string.
     *
     * @return (StreamBlock) -> String
     */
    val serializer: (StreamBlock) -> String =
        { block: StreamBlock -> moshi.adapter(StreamBlock::class.java).toJson(block) }

    /**
     * Computes and returns the starting height (if it can be determined) to be used when streaming historical blocks.
     *
     * @return Long? The starting block height to use, if it exists.
     */
    private fun getStartingHeight(): Long? = options.fromHeight

    private fun getEndingHeight(): Long = options.toHeight ?: Long.MAX_VALUE

    fun streamHistoricalBlocks(fromHeight: Long, toHeight: Long): Flow<StreamBlock> = flow {
        HistoricalBlockSource(
            dispatchers,
            blockFetcher,
            tendermintServiceClient,
            fromHeight,
            toHeight,
            options.batchSize,
            options.skipIfEmpty,
            options.concurrency,
        ).streamBlocks().collect { emit(it) }
    }

    fun streamLiveBlocks(): Flow<StreamBlock> = flow {
        LiveBlockSource(
            moshi,
            eventStreamService,
            blockFetcher,
            dispatchers
        ).streamBlocks().collect { emit(it) }
    }

    /**
     * Constructs a Flow of live and historical blocks, plus associated event data.
     *
     * If a starting height is provided, historical blocks will be included in the Flow from the starting height, up
     * to the latest block height determined at the start of the collection of the Flow.
     *
     * @return A Flow of live and historical blocks, plus associated event data.
     */
    override fun streamBlocks(): Flow<StreamBlock> = flow {
        val startingHeight: Long? = getStartingHeight()
        val endingHeight: Long = getEndingHeight()
        emitAll(
            if (startingHeight != null) {                    
                log.info("Listening for live and historical blocks from height $startingHeight")
                merge(streamHistoricalBlocks(startingHeight, endingHeight), streamLiveBlocks())
            } else {
                log.info("Listening for live blocks only")
                streamLiveBlocks()
            }
        )
    }.cancellable().retryWhen { cause: Throwable, attempt: Long ->
        log.warn("streamBlocks::error; recovering Flow (attempt ${attempt + 1})")
        when (cause) {
            is EOFException, is CompletionException, is ConnectException, is SocketTimeoutException, is SocketException -> {
                val duration = backoff(attempt, jitter = false)
                log.error("Reconnect attempt #$attempt; waiting ${duration.inWholeSeconds}s before trying again: $cause")
                delay(duration)
                true
            }
            else -> false
        }
    }
}
