package io.provenance.eventstream.stream

import arrow.core.Either
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.flow.extensions.chunked
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.EncodedBlockchainEvent
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.models.TxEvent
import io.provenance.eventstream.stream.models.extensions.blockEvents
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.stream.models.extensions.txEvents
import io.provenance.eventstream.stream.models.extensions.txHash
import io.provenance.eventstream.stream.models.rpc.request.Subscribe
import io.provenance.eventstream.stream.models.rpc.response.MessageType
import io.provenance.eventstream.utils.backoff
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException
import kotlin.time.Duration

@OptIn(FlowPreview::class, ExperimentalTime::class)
@ExperimentalCoroutinesApi
class EventStream(
    private val eventStreamService: EventStreamService,
    private val tendermintServiceClient: TendermintServiceClient,
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val options: Options = Options.DEFAULT
) : BlockSource<StreamBlockImpl> {
    companion object {
        /**
         * The default number of blocks that will be contained in a batch.
         */
        const val DEFAULT_BATCH_SIZE = 8

        /**
         * The maximum size of the query range for block heights allowed by the Tendermint API.
         * This means, for a given block height `H`, we can ask for blocks in the range [`H`, `H` + `TENDERMINT_MAX_QUERY_RANGE`].
         * Requesting a larger range will result in the API emitting an error.
         */
        const val TENDERMINT_MAX_QUERY_RANGE = 20
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
     * A decoder for Tendermint RPC API messages.
     */
    private val responseMessageDecoder: MessageType.Decoder = MessageType.Decoder(moshi)

    /**
     * A serializer function that converts a [StreamBlockImpl] instance to a JSON string.
     *
     * @return (StreamBlock) -> String
     */
    val serializer: (StreamBlockImpl) -> String =
        { block: StreamBlockImpl -> moshi.adapter(StreamBlockImpl::class.java).toJson(block) }

    /**
     * Computes and returns the starting height (if it can be determined) to be used when streaming historical blocks.
     *
     * @return Long? The starting block height to use, if it exists.
     */
    private fun getStartingHeight(): Long? = options.fromHeight

    /**
     * Computes and returns the ending height (if it can be determined) tobe used when streaming historical blocks.
     *
     * @return Long? The ending block height to use, if it exists.
     */
    private suspend fun getEndingHeight(): Long? =
        options.toHeight ?: tendermintServiceClient.abciInfo().result?.response?.lastBlockHeight

    /**
     * Returns a sequence of block height pairs [[low, high]], representing a range to query when searching for blocks.
     */
    fun getBlockHeightQueryRanges(minHeight: Long, maxHeight: Long): Sequence<Pair<Long, Long>> {
        if (minHeight > maxHeight) {
            return emptySequence()
        }
        val step = TENDERMINT_MAX_QUERY_RANGE
        return sequence {
            var i = minHeight
            var j = i + step - 1
            while (j <= maxHeight) {
                yield(Pair(i, j))
                i = j + 1
                j = i + step - 1
            }
            // If there's a gap between the last range and `maxHeight`, yield one last pair to fill it:
            if (i <= maxHeight) {
                yield(Pair(i, maxHeight))
            }
        }
    }

    /**
     * Returns the heights of all existing blocks in a height range [[low, high]], subject to certain conditions.
     *
     * - If [Options.skipIfEmpty] is true, only blocks which contain 1 or more transactions will be returned.
     *
     * @return A list of block heights
     */
    private suspend fun getBlockHeightsInRange(minHeight: Long, maxHeight: Long): List<Long> {
        if (minHeight > maxHeight) {
            return emptyList()
        }

        // invariant
        assert((maxHeight - minHeight) <= TENDERMINT_MAX_QUERY_RANGE) {
            "Difference between (minHeight, maxHeight) can be at maximum $TENDERMINT_MAX_QUERY_RANGE"
        }

        val blocks = tendermintServiceClient.blockchain(minHeight, maxHeight).result?.blockMetas.let {
            if (options.skipIfEmpty) {
                it?.filter { it.numTxs ?: 0 > 0 }
            } else {
                it
            }
        }?.mapNotNull { it.header?.height } ?: emptyList()

        return blocks.sortedWith(naturalOrder())
    }

    /**
     * Test if any block events match the supplied predicate.
     *
     * @return True or false if [Options.blockEventPredicate] matches a block-level event associated with a block.
     * If the return value is null, then [Options.blockEventPredicate] was never set.
     */
    private fun <T : EncodedBlockchainEvent> matchesBlockEvent(blockEvents: List<T>): Boolean? =
        options.blockEventPredicate?.let { p ->
            if (options.skipIfEmpty) {
                blockEvents.any { p(it.eventType) }
            } else {
                blockEvents.isEmpty() || blockEvents.any { p(it.eventType) }
            }
        }

    /**
     * Test if any transaction events match the supplied predicate.
     *
     * @return True or false if [Options.txEventPredicate] matches a transaction-level event associated with a block.
     * If the return value is null, then [Options.txEventPredicate] was never set.
     */
    private fun <T : EncodedBlockchainEvent> matchesTxEvent(txEvents: List<T>): Boolean? =
        options.txEventPredicate?.let { p ->
            if (options.skipIfEmpty) {
                txEvents.any { p(it.eventType) }
            } else {
                txEvents.isEmpty() || txEvents.any { p(it.eventType) }
            }
        }

    /**
     * Query a block by height, returning any events associated with the block.
     *
     *  @param heightOrBlock Fetch a block, plus its events, by its height or the `Block` model itself.
     *  @param skipIfNoTxs If [skipIfNoTxs] is true, if the block at the given height has no transactions, null will
     *  be returned in its place.
     */
    private suspend fun queryBlock(
        heightOrBlock: Either<Long, Block>,
        skipIfNoTxs: Boolean = true,
        historical: Boolean = false
    ): StreamBlockImpl? {
        val block: Block? = when (heightOrBlock) {
            is Either.Left<Long> -> tendermintServiceClient.block(heightOrBlock.value).result?.block
            is Either.Right<Block> -> heightOrBlock.value
        }

        if (skipIfNoTxs && (block?.data?.txs?.size ?: 0) == 0) {
            return null
        }

        return block?.run {
            val blockDatetime = header?.dateTime()
            val blockResponse = tendermintServiceClient.blockResults(header?.height).result
            val blockEvents: List<BlockEvent> = blockResponse.blockEvents(blockDatetime)
            val txEvents: List<TxEvent> = blockResponse.txEvents(blockDatetime) { index: Int -> txHash(index) ?: "" }
            val streamBlock = StreamBlockImpl(this, blockEvents, txEvents, historical)
            val matchBlock = matchesBlockEvent(blockEvents)
            val matchTx = matchesTxEvent(txEvents)

            // ugly:
            if ((matchBlock == null && matchTx == null) || (matchBlock == null && matchTx != null && matchTx) || (matchBlock != null && matchBlock && matchTx == null) || (matchBlock != null && matchBlock && matchTx != null && matchTx)) {
                streamBlock
            } else {
                null
            }
        }
    }

    /***
     * Query a collections of blocks by their heights.
     *
     * Note: it is assumed the specified blocks already exists. No check will be performed to verify existence!
     *
     * @param blockHeights The heights of the blocks to query, along with optional metadata to attach to the fetched
     *  block data.
     * @return A Flow of found historical blocks along with events associated with each block, if any.
     */
    private fun queryBlocks(blockHeights: Iterable<Long>): Flow<StreamBlockImpl> =
        blockHeights.chunked(options.batchSize).asFlow().transform { chunkOfHeights: List<Long> ->
            emitAll(
                coroutineScope {
                    // Concurrently process <batch-size> blocks at a time:
                    chunkOfHeights.map { height ->
                        async { queryBlock(Either.Left(height), skipIfNoTxs = options.skipIfEmpty, historical = true) }
                    }.awaitAll().filterNotNull()
                }.asFlow()
            )
        }.flowOn(dispatchers.io())

    /**
     * Constructs a Flow of historical blocks and associated events based on a starting height.
     *
     * Blocks will be streamed from the given starting height up to the latest block height,
     * as determined by the start of the Flow.
     *
     * If no ending height could be found, an exception will be raised.
     *
     * @return A flow of historical blocks
     */
    fun streamHistoricalBlocks(): Flow<StreamBlockImpl> = flow {
        val startHeight: Long = getStartingHeight() ?: run {
            log.warn("No starting height provided; defaulting to 0")
            0
        }
        val endHeight: Long = getEndingHeight() ?: error("Couldn't determine ending height")
        emitAll(streamHistoricalBlocks(startHeight, endHeight))
    }

    private fun streamHistoricalBlocks(startHeight: Long): Flow<StreamBlockImpl> = flow {
        val endHeight: Long = getEndingHeight() ?: error("Couldn't determine ending height")
        emitAll(streamHistoricalBlocks(startHeight, endHeight))
    }

    private fun streamHistoricalBlocks(startHeight: Long, endHeight: Long): Flow<StreamBlockImpl> {
        log.info("historical::streaming blocks from $startHeight to $endHeight")
        log.info("historical::batch size = ${options.batchSize}")

        return MetadataStream(
            startHeight,
            endHeight,
            options.skipIfEmpty,
            options.concurrency,
            options.batchSize,
            tendermintServiceClient
        ).streamBlocks().chunked(options.batchSize, Duration.parse("PT0.1S")).map { blockmetas ->
                blockmetas.map {
                    it.header!!.height } }
            .flowOn(dispatchers.io()).flatMapMerge(options.concurrency) {
                queryBlocks(it) }.flowOn(dispatchers.io())
            .onCompletion { cause: Throwable? ->
                if (cause == null) {
                    log.info("historical::exhausted historical block stream ok")
                } else {
                    log.error("historical::exhausted block stream with error: ${cause.message}")
                }
            }
    }

    /**
     * Constructs a Flow of newly minted blocks and associated events as the blocks are added to the chain.
     *
     * @return A Flow of newly minted blocks and associated events
     */
    fun streamLiveBlocks(): Flow<StreamBlockImpl> {

        // Toggle the Lifecycle register start state:
        eventStreamService.startListening()

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
        }.flowOn(dispatchers.io()).onStart { log.info("live::starting") }.mapNotNull { block: Block ->
            val maybeBlock = queryBlock(Either.Right(block), skipIfNoTxs = false, historical = false)
            if (maybeBlock != null) {
                log.info("live::got block #${maybeBlock.height}")
                maybeBlock
            } else {
                log.info("live::skipping block #${block.header?.height}")
                null
            }
        }.onCompletion {
            log.info("live::stopping event stream")
            eventStreamService.stopListening()
        }.retryWhen { cause: Throwable, attempt: Long ->
            log.warn("live::error; recovering Flow (attempt ${attempt + 1})")
            when (cause) {
                is JsonDataException -> {
                    log.error("streamLiveBlocks::parse error, skipping: $cause")
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Constructs a Flow of live and historical blocks, plus associated event data.
     *
     * If a starting height is provided, historical blocks will be included in the Flow from the starting height, up
     * to the latest block height determined at the start of the collection of the Flow.
     *
     * @return A Flow of live and historical blocks, plus associated event data.
     */
    override fun streamBlocks(): Flow<StreamBlockImpl> = flow {
        val startingHeight: Long? = getStartingHeight()
        emitAll(
            if (startingHeight != null) {
                log.info("Listening for live and historical blocks from height $startingHeight")
                merge(streamHistoricalBlocks(startingHeight), streamLiveBlocks())
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
