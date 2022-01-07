package io.provenance.eventstream.stream

import com.squareup.moshi.JsonDataException
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.stream.clients.BlockData
import io.provenance.eventstream.stream.clients.BlockFetcher
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.EncodedBlockchainEvent
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.extensions.blockEvents
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.stream.models.extensions.txEvents
import io.provenance.eventstream.stream.models.extensions.txHash
import io.provenance.eventstream.stream.models.rpc.request.Subscribe
import io.provenance.eventstream.stream.models.rpc.response.MessageType
import io.provenance.eventstream.utils.backoff
import kotlinx.coroutines.*
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException

@OptIn(FlowPreview::class, ExperimentalTime::class)
@ExperimentalCoroutinesApi
class EventStream(
    private val eventStreamService: EventStreamService,
    private val blockFetcher: BlockFetcher,
    private val decoder: DecoderEngine,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val checkpoint: Checkpoint = FileCheckpoint(),
    private val options: BlockStreamOptions = BlockStreamOptions()
) : BlockSource {

    private val log = KotlinLogging.logger { }

    /**
     * A decoder for Tendermint RPC API messages.
     */
    private val responseMessageDecoder: MessageType.Decoder = MessageType.Decoder(decoder)

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

    private fun StreamBlock.isEmpty() = block.data?.txs?.isEmpty() ?: true

    private fun Flow<StreamBlock>.filterNonEmptyIfSet(): Flow<StreamBlock> =
        filter { !(options.skipEmptyBlocks && it.isEmpty()) }

    private fun Flow<StreamBlock>.filterByEvents(): Flow<StreamBlock> =
        filter { keepBlock(it.txEvents + it.blockEvents) }

    private fun BlockData.toStreamBlock(): StreamBlock {
        val blockDatetime = block.header?.dateTime()
        val blockEvents = blockResult.blockEvents(blockDatetime)
        val txEvents = blockResult.txEvents(blockDatetime) { index: Int -> block.txHash(index).orEmpty() }
        return StreamBlock(block, blockEvents, txEvents)
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
        blockHeights.chunked(options.batchSize).asFlow().transform { chunkOfHeights: List<Long> ->
            emitAll(
                coroutineScope {
                    // Concurrently process <batch-size> blocks at a time:
                    chunkOfHeights.map { height ->
                        async { blockFetcher.getBlock(height).toStreamBlock() }
                    }.awaitAll()
                }.asFlow()
            )
        }.flowOn(dispatchers.io())

    private suspend fun <T, R> Flow<T>.doFlatmap(transform: suspend (value: T) -> Flow<R>): Flow<R> {
        return if (options.ordered) {
            flatMapConcat { transform(it) }
        } else {
            flatMapMerge(options.concurrency) { transform(it) }
        }
    }

    suspend fun streamHistoricalBlocks(startHeight: Long, endHeight: Long): Flow<StreamBlock> = flow {
        log.info("historical::streaming blocks from $startHeight to $endHeight")
        log.info("historical::batch size = ${options.batchSize}")

        (startHeight..endHeight)
            .chunked(options.batchSize)
            .asFlow()
            .flowOn(dispatchers.io())
            .doFlatmap { queryBlocks(it).map { b -> b.copy(historical = true) } }
            .flowOn(dispatchers.io())
            .filterByEvents()
            .filterNonEmptyIfSet()
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
    suspend fun streamLiveBlocks(): Flow<StreamBlock> {

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
            blockFetcher.getBlock(block.header!!.height).toStreamBlock().also {
                log.info("live::got block #${it.height}")
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
    override suspend fun streamBlocks(from: Long?, toInclusive: Long?): Flow<StreamBlock> = channelFlow {
        val liveChannel = Channel<StreamBlock>(720)
        val liveJob = async {
            streamLiveBlocks()
                .buffer()
                .onCompletion { liveChannel.close(it) }
                .collect { liveChannel.send(it) }
        }

        val currentHeight = blockFetcher.getCurrentHeight()
        val needHistory = from != null && from <= currentHeight
        val needLive = toInclusive == null || toInclusive > currentHeight
        if (!needLive) {
            liveJob.cancel()
            log.trace("streamblocks::live cancelled: not needed")
        }

        val historyChannel = Channel<StreamBlock>()
        val historyJob = async(start = CoroutineStart.LAZY) {
            val calculatedFrom = checkpoint.lastCheckpoint() ?: (from ?: currentHeight)
            val calculatedTo = toInclusive ?: currentHeight

            log.info("hist::calculated-from:$calculatedFrom calculated-to:$calculatedTo need-history:$needHistory need-live:$needLive")
            streamHistoricalBlocks(calculatedFrom, calculatedTo)
                .buffer()
                .onCompletion { historyChannel.close(it) }
                .collect { historyChannel.send(it) }
        }

        if (needHistory) {
            historyJob.start()
            historyChannel.consumeAsFlow().collect { send(it) }
        }

        if (needLive) {
            // Make sure we pull anything between the last history and the first live
            // TODO
            // val liveStart = liveChannel.consumeAsFlow().peek().height
            liveChannel.consumeAsFlow().collect { send(it) }
        }
    }
        .cancellable()
        .onEach {
            if (it.height % checkpoint.checkEvery == 0L) {
                checkpoint.checkpoint(it.height)
            }
        }
        .retryWhen { cause: Throwable, attempt: Long ->
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
