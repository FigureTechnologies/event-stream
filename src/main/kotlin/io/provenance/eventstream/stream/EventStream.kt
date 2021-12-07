package io.provenance.eventstream.stream

import com.squareup.moshi.JsonDataException
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.provenance.eventstream.DefaultDispatcherProvider
import io.provenance.eventstream.DispatcherProvider
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.flow.extensions.chunked
import io.provenance.eventstream.info
import io.provenance.eventstream.logger
import io.provenance.eventstream.stream.clients.TendermintServiceClient
import io.provenance.eventstream.stream.models.*
import io.provenance.eventstream.stream.models.extensions.blockEvents
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.stream.models.extensions.txEvents
import io.provenance.eventstream.stream.models.extensions.txHash
import io.provenance.eventstream.utils.backoff
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@OptIn(FlowPreview::class, ExperimentalTime::class)
@ExperimentalCoroutinesApi
class EventStream(
    private val eventStreamService: EventStreamService,
    private val blockFetcher: BlockFetcher,
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
    private suspend fun queryBlock(height: Long): StreamBlock =
        blockFetcher.getBlock(height).toStreamBlock()

    private fun StreamBlock.isEmpty() = block.data?.txs?.isEmpty() ?: true

    private fun Flow<StreamBlock>.filterNonEmptyIfSet(): Flow<StreamBlock> = filter {
        if (options.skipEmptyBlocks) !it.isEmpty()
        else true
    }
    
    /**
     *
     */
    private fun BlockData.toStreamBlock(): StreamBlock {
        val blockDatetime = block.header?.dateTime()
        val blockEvents = blockResult.blockEvents(blockDatetime)
        val txEvents = blockResult.txEvents(blockDatetime) { index: Int -> block.txHash(index).orEmpty() }
        return StreamBlock(block, blockEvents, txEvents)
    }

    private suspend fun queryBlocks(blockHeights: Iterable<Long>): Flow<StreamBlock> =
        blockHeights
            .chunked(options.batchSize)
            .asFlow()
            .transform {
                // Concurrently process <batch-size> blocks at a time:
                val blocks = coroutineScope { it.map { height -> async { queryBlock(height) } }.awaitAll() }
                emitAll(blocks.asFlow())
            }
            .catch { e -> log.error("", e) }
            .flowOn(dispatchers.io())

    private suspend fun streamHistoricalBlocks(startHeight: Long, endHeight: Long): Flow<StreamBlock> {
        log.info("historical::streaming blocks from $startHeight to $endHeight")
        return (startHeight..endHeight).asFlow()
            .chunked(options.batchSize)
            .flatMapMerge(options.concurrency) { queryBlocks(it).map { b -> b.copy(historical = true) } }
            .catch { e -> log.error("", e) }
            .flowOn(dispatchers.io())
            .onStart { log.info { "historical::starting" } }
            .onCompletion { cause: Throwable? ->
                if (cause == null) {
                    log.info("historical::exhausted historical block stream ok")
                } else {
                    log.error("historical::exhausted block stream with error: ${cause.message}")
                }
            }.retryWhen { cause, attempt ->
                log.error("attempt: $attempt", cause)
                true
            }
    }

    /**
     * Constructs a Flow of newly minted blocks and associated events as the blocks are added to the chain.
     *
     * @return A Flow of newly minted blocks and associated events
     */
    suspend fun streamLiveBlocks(): Flow<StreamBlock> {
        log.info("live::start")
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
                val maybeBlock = queryBlock(block.header?.height!!)
                if (maybeBlock != null) {
                    log.info("live::got block #${maybeBlock.height}")
                    maybeBlock
                } else {
                    log.info("live::skipping block #${block.header.height}")
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
    override suspend fun streamBlocks(from: Long, toInclusive: Long?): Flow<StreamBlock> = coroutineScope {
        flow {
            val liveChannel = Channel<StreamBlock>(720)
            val liveJob = launch {
                streamLiveBlocks().buffer().flowOn(dispatchers.io()).collect { liveChannel.send(it) }
            }

            val currentHeight = tendermintServiceClient.abciInfo().result?.response?.lastBlockHeight
                ?: throw RuntimeException("could not fetch current height")

            val realTo = toInclusive ?: currentHeight
            val needLive = toInclusive != null && toInclusive > currentHeight

            log.info("realTo:$realTo need-live:$needLive")
            emitAll(streamHistoricalBlocks(from, realTo))
            if (needLive) {
                emitAll(liveChannel)
            } else {
                liveJob.cancel()
                liveChannel.close()
            }
        }
            .filterNonEmptyIfSet()
            .cancellable()
            .retryWhen { cause: Throwable, attempt: Long ->
                log.warn("streamBlocks::error; recovering Flow (attempt ${attempt + 1})", cause)
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
}
