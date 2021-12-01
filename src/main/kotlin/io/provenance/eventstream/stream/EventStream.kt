package io.provenance.eventstream.stream

import arrow.core.Either
import com.squareup.moshi.JsonDataException
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.provenance.eventstream.DefaultDispatcherProvider
import io.provenance.eventstream.DispatcherProvider
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.logger
import io.provenance.eventstream.stream.clients.TendermintServiceClient
import io.provenance.eventstream.stream.models.*
import io.provenance.eventstream.stream.models.extensions.blockEvents
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.stream.models.extensions.txEvents
import io.provenance.eventstream.stream.models.extensions.txHash
import io.provenance.eventstream.utils.backoff
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.ExperimentalTime

@OptIn(FlowPreview::class, ExperimentalTime::class)
@ExperimentalCoroutinesApi
class EventStream(
    private val eventStreamService: EventStreamService,
    private val tendermintServiceClient: TendermintServiceClient,
    private val decoder: DecoderEngine,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val options: BlockStreamOptions = BlockStreamOptions()
) : BlockSource {
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

    private val log = logger()

    /**
     * A decoder for Tendermint RPC API messages.
     */
    private val responseMessageDecoder: MessageType.Decoder = MessageType.Decoder(decoder)

    /**
     * A serializer function that converts a [StreamBlock] instance to a JSON string.
     *
     * @return (StreamBlock) -> String
     */
    val serializer: (StreamBlock) -> String =
        { block: StreamBlock -> decoder.adapter(StreamBlock::class).toJson(block) }

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
    private fun getBlockHeightQueryRanges(minHeight: Long, maxHeight: Long): Sequence<Pair<Long, Long>> {
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

        return (tendermintServiceClient.blockchain(minHeight, maxHeight)
            .result
            ?.blockMetas
            .let {
                if (options.skipEmptyBlocks) {
                    it?.filter { it.numTxs ?: 0 > 0 }
                } else {
                    it
                }
            }?.mapNotNull { it.header?.height }
            ?: emptyList<Long>())
            .sortedWith(naturalOrder<Long>())
    }

    private fun <T : EncodedBlockchainEvent> keepBlock(events: List<T>): Boolean {
        if (options.txEvents.isEmpty() && options.blockEvents.isEmpty()) {
            return true
        }

        if (options.txEvents.isNotEmpty() && events.any { it.eventType in options.txEvents }) {
            return true
        }

        if (options.blockEvents.isNotEmpty() && events.any { it.eventType in options.blockEvents }) {
            return true
        }

        return false
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
        skipIfNoTxs: Boolean = options.skipEmptyBlocks,
    ): StreamBlock? {
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
            val blockEvents = blockResponse.blockEvents(blockDatetime)
            val txEvents = blockResponse.txEvents(blockDatetime) { index: Int -> txHash(index) ?: "" }
            val streamBlock = StreamBlock(this, blockEvents, txEvents)
            if (keepBlock(txEvents + blockEvents)) {
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
    private fun queryBlocks(blockHeights: Iterable<Long>): Flow<StreamBlock> =
        blockHeights
            .chunked(options.batchSize)
            .asFlow()
            .transform { chunkOfHeights: List<Long> ->
                emitAll(
                    coroutineScope {
                        // Concurrently process <batch-size> blocks at a time:
                        chunkOfHeights.map { height ->
                            async { queryBlock(Either.Left(height), skipIfNoTxs = options.skipEmptyBlocks) }
                        }
                            .awaitAll()
                            .filterNotNull()
                    }.asFlow()
                )
            }
            .flowOn(dispatchers.io())

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
    fun streamHistoricalBlocks(): Flow<StreamBlock> = flow {
        val startHeight: Long = getStartingHeight() ?: run {
            log.warn("No starting height provided; defaulting to 0")
            0
        }
        val endHeight: Long = getEndingHeight() ?: error("Couldn't determine ending height")
        emitAll(streamHistoricalBlocks(startHeight, endHeight))
    }

    private fun streamHistoricalBlocks(startHeight: Long): Flow<StreamBlock> = flow {
        val endHeight: Long = getEndingHeight() ?: error("Couldn't determine ending height")
        emitAll(streamHistoricalBlocks(startHeight, endHeight))
    }

    private fun streamHistoricalBlocks(startHeight: Long, endHeight: Long): Flow<StreamBlock> = flow {
        log.info("historical::streaming blocks from $startHeight to $endHeight")
        log.info("historical::batch size = ${options.batchSize}")

        // We're only allowed to query  a block range of (highBlockHeight - lowBlockHeight) = `TENDERMINT_MAX_QUERY_RANGE`
        // max block heights in a single request. If `Options.batchSize` is greater than this value, then we need to
        // make N calls to tendermint to the Tendermint API to have enough blocks to meet batchSize.
        val limit1 = TENDERMINT_MAX_QUERY_RANGE.toDouble()
        val limit2 = options.batchSize.toDouble()
        val numChunks: Int = floor(max(limit1, limit2) / min(limit1, limit2)).toInt()

        emitAll(getBlockHeightQueryRanges(startHeight, endHeight).chunked(numChunks).asFlow())
    }
        .map { heightPairChunk: List<Pair<Long, Long>> ->
            val availableBlocks: List<Long> = coroutineScope {
                heightPairChunk
                    .map { (minHeight, maxHeight) -> async { getBlockHeightsInRange(minHeight, maxHeight) } }
                    .awaitAll()
                    .flatten()
            }
            log.info("historical::${availableBlocks.size} block(s) in [${heightPairChunk.minOf { it.first }}..${heightPairChunk.maxOf { it.second }}]")
            availableBlocks
        }
        .flowOn(dispatchers.io())
        .flatMapMerge(options.concurrency) { queryBlocks(it) }
        .flowOn(dispatchers.io())
        .map { it.copy(historical = true) }
        .onCompletion { cause: Throwable? ->
            if (cause == null) {
                log.info("historical::exhausted historical block stream ok")
            } else {
                log.error("historical::exhausted block stream with error: ${cause.message}")
            }
        }

    /**
     * Constructs a Flow of newly minted blocks and associated events as the blocks are added to the chain.
     *
     * @return A Flow of newly minted blocks and associated events
     */
    fun streamLiveBlocks(): Flow<StreamBlock> {

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
                    is WebSocket.Event.OnMessageReceived ->
                        when (val message = event.message) {
                            is Message.Text -> {
                                when (val type = responseMessageDecoder.decode(message.value)) {
                                    is MessageType.Empty ->
                                        log.info("received empty ACK message => ${message.value}")
                                    is MessageType.NewBlock -> {
                                        val block = type.block.data.value.block
                                        log.info("live::received NewBlock message: #${block.header?.height}")
                                        send(block)
                                    }
                                    is MessageType.Error ->
                                        log.error("upstream error from RPC endpoint: ${type.error}")
                                    is MessageType.Panic -> {
                                        log.error("upstream panic from RPC endpoint: ${type.error}")
                                        throw CancellationException("RPC endpoint panic: ${type.error}")
                                    }
                                    is MessageType.Unknown ->
                                        log.info("unknown message type; skipping message => ${message.value}")
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
            .flowOn(dispatchers.io())
            .onStart { log.info("live::starting") }
            .mapNotNull { block: Block ->
                val maybeBlock = queryBlock(Either.Right(block), skipIfNoTxs = false)
                if (maybeBlock != null) {
                    log.info("live::got block #${maybeBlock.height}")
                    maybeBlock
                } else {
                    log.info("live::skipping block #${block.header?.height}")
                    null
                }
            }
            .onCompletion {
                log.info("live::stopping event stream")
                eventStreamService.stopListening()
            }
            .retryWhen { cause: Throwable, attempt: Long ->
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
    override suspend fun streamBlocks(): Flow<StreamBlock> = flow {
        val startingHeight: Long? = getStartingHeight()

        if (startingHeight != null) {
            log.info("Listening for live and historical blocks from height $startingHeight")
            emitAll(streamHistoricalBlocks(startingHeight))
        }

        log.info("Listening for live blocks")
        emitAll(streamLiveBlocks())
    }
        .cancellable()
        .retryWhen { cause: Throwable, attempt: Long ->
            log.warn("streamBlocks::error; recovering Flow (attempt ${attempt + 1})")
            when (cause) {
                is EOFException,
                is CompletionException,
                is ConnectException,
                is SocketTimeoutException,
                is SocketException -> {
                    val duration = backoff(attempt, jitter = false)
                    log.error("Reconnect attempt #$attempt; waiting ${duration.inWholeSeconds}s before trying again: $cause")
                    delay(duration)
                    true
                }
                else -> false
            }
        }
}

