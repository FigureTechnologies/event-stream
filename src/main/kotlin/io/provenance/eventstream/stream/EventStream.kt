package io.provenance.eventstream.stream

import com.squareup.moshi.JsonDataException
import com.tinder.scarlet.Message
import com.tinder.scarlet.WebSocket
import io.provenance.eventstream.DefaultDispatcherProvider
import io.provenance.eventstream.DispatcherProvider
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.logger
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.MessageType
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.Subscribe
import io.provenance.eventstream.stream.models.extensions.blockEvents
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.stream.models.extensions.txEvents
import io.provenance.eventstream.stream.models.extensions.txHash
import io.provenance.eventstream.utils.backoff
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.EOFException
import java.io.File
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.ExperimentalTime

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
    companion object {
        /**
         * The default number of blocks that will be contained in a batch.
         */
        const val DEFAULT_BATCH_SIZE = 128
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

    /**
     *
     */
    private fun BlockData.toStreamBlock(): StreamBlock {
        val blockDatetime = block.header?.dateTime()
        val blockEvents = blockResult.blockEvents(blockDatetime)
        val txEvents = blockResult.txEvents(blockDatetime) { index: Int -> block.txHash(index).orEmpty() }
        return StreamBlock(block, blockEvents, txEvents)
    }

    private suspend fun queryBlocks(blockHeights: List<Long>): Flow<StreamBlock> =
        blockFetcher.getBlocks(blockHeights).map { it.toStreamBlock() }

    private suspend fun <T, R> Flow<T>.doFlatmap(transform: suspend (value: T) -> Flow<R>): Flow<R> {
        return if (options.ordered) {
            flatMapConcat { transform(it) }
        } else {
            flatMapMerge(options.concurrency) { transform(it) }
        }
    }

    suspend fun streamHistoricalBlocks(startHeight: Long, endHeight: Long): Flow<StreamBlock> =
        (startHeight..endHeight)
            .chunked(options.batchSize)
            .asFlow()
            .doFlatmap { queryBlocks(it).map { b -> b.copy(historical = true) } }
            .filterNonEmptyIfSet()
            .filterByEvents()

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
                        log.info("live::initializing subscription for tm.event='NewBlock'")
                        eventStreamService.subscribe(Subscribe("tm.event='NewBlock'"))
                    }
                    is WebSocket.Event.OnMessageReceived ->
                        when (val message = event.message) {
                            is Message.Bytes -> log.warn("live::binary message payload not supported")
                            is Message.Text -> {
                                when (val type = responseMessageDecoder.decode(message.value)) {
                                    is MessageType.NewBlock -> {
                                        val block = type.block.data.value.block
                                        log.debug("live::received NewBlock message: #${block.header?.height}")
                                        send(block)
                                    }
                                    is MessageType.Empty -> log.info("live::received empty ACK message => ${message.value}")
                                    is MessageType.Error -> log.error("live::upstream error from RPC endpoint: ${type.error}")
                                    is MessageType.Unknown -> log.info("live::unknown message type; skipping message => ${message.value}")
                                    is MessageType.Panic -> {
                                        log.error("live::upstream panic from RPC endpoint: ${type.error}")
                                        throw CancellationException("RPC endpoint panic: ${type.error}")
                                    }
                                }
                            }
                        }
                    is WebSocket.Event.OnConnectionFailed -> throw event.throwable
                    else -> throw Throwable("live::unexpected event type: $event")
                }
            }
        }
            .mapNotNull { block: Block ->
                blockFetcher.getBlock(block.header?.height!!).toStreamBlock().also {
                    log.debug("live::got block #${it.height}")
                }
            }
            .filterNonEmptyIfSet()
            .filterByEvents()
            .onCompletion {
                log.debug("live::stopping event stream")
                eventStreamService.stopListening()
            }
            .retryWhen { cause: Throwable, attempt: Long ->
                log.warn("live::error; recovering Flow (attempt ${attempt + 1})")
                when (cause) {
                    is JsonDataException -> {
                        log.error("live::parse error, skipping: $cause")
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
        .buffer()
        .onEach {
            if (it.height % checkpoint.checkEvery == 0L) {
                checkpoint.checkpoint(it.height)
            }
        }
        .retryWhen { cause: Throwable, attempt: Long ->
            log.warn("streamBlocks::error; recovering Flow (attempt ${attempt + 1})", cause)
            when (cause) {
                is EOFException,
                is CompletionException,
                is ConnectException,
                is SocketTimeoutException,
                is SocketException -> {
                    val duration = backoff(attempt, jitter = false)
                    log.error("streamblocks::Reconnect attempt #$attempt; waiting ${duration.inWholeSeconds}s before trying again: $cause")
                    delay(duration)
                    true
                }
                else -> false
            }
        }
}

suspend fun <T> Flow<T>.mirror(n: Int = 1): List<Flow<T>> {
    val orig = this
    val mirrored = (0 until n).map {
        flow {
            orig.onEach { emit(it) }.collect()
        }
    }
    return listOf(this) + mirrored
}

suspend fun <T> Flow<T>.peek(): T? {
    val (_, n) = mirror()
    return n.first()
}