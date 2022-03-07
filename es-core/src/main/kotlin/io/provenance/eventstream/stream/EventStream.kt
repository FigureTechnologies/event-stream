package io.provenance.eventstream.stream

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.config.Options
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.extensions.doFlatMap
import io.provenance.eventstream.flow.extensions.chunked
import io.provenance.eventstream.stream.clients.BlockData
import io.provenance.eventstream.stream.clients.TendermintBlockFetcher
import io.provenance.eventstream.stream.models.*
import io.provenance.eventstream.stream.models.extensions.blockEvents
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.stream.models.extensions.txEvents
import io.provenance.eventstream.stream.models.extensions.txHash
import io.provenance.eventstream.stream.transformers.queryBlock
import io.provenance.eventstream.utils.backoff
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlin.time.ExperimentalTime
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
    private val fetcher: TendermintBlockFetcher,
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

    private val log = KotlinLogging.logger { }

    /**
     * A serializer function that converts a [StreamBlockImpl] instance to a JSON string.
     *
     * @return (StreamBlock) -> String
     */
    val serializer: (StreamBlockImpl) -> String =
        { block: StreamBlockImpl -> moshi.adapter(StreamBlockImpl::class.java).toJson(block) }

    /***
     * Query a collections of blocks by their heights.
     *
     * Note: it is assumed the specified blocks already exists. No check will be performed to verify existence!
     *
     * @param blockHeights The heights of the blocks to query, along with optional metadata to attach to the fetched
     *  block data.
     * @return A Flow of found historical blocks along with events associated with each block, if any.
     */
    private suspend fun queryBlocks(blockHeights: List<Long>): Flow<StreamBlockImpl> =
        fetcher.getBlocks(blockHeights).map { it.toStreamBlock() }

    fun streamLiveBlocks(): Flow<StreamBlockImpl> {
        return streamLiveMetaBlocks()
            .toLiveStream()
    }

    suspend fun streamHistoricalBlocks(startingHeight: Long): Flow<StreamBlockImpl> {
        return streamMetaBlocks()
            .toHistoricalStream(startingHeight)
    }

    fun streamLiveMetaBlocks(): Flow<Block> {
        return LiveMetaDataStream(eventStreamService, moshi).streamBlocks()
    }

    fun streamMetaBlocks(): Flow<BlockMeta> {
        return MetadataStream(options, fetcher).streamBlocks()
    }

    private suspend fun <T, R> Flow<T>.doFlatmap(transform: suspend (value: T) -> Flow<R>): Flow<R> {
        return if (options.ordered) {
            flatMapConcat { transform(it) }
        } else {
            flatMapMerge(options.concurrency) { transform(it) }
        }
    }

    private fun StreamBlock.isEmpty() = block.data?.txs?.isEmpty() ?: true

    private fun Flow<StreamBlock>.filterNonEmptyIfSet(): Flow<StreamBlock> =
        filter { !(options.skipEmptyBlocks && it.isEmpty()) }

    private fun Flow<StreamBlock>.filterByEvents(): Flow<StreamBlock> =
        filter { keepBlock(it.txEvents + it.blockEvents) }

    private fun <T : EncodedBlockchainEvent> keepBlock(events: List<T>): Boolean {
        if (options.txEventPredicate.isEmpty() && options.blockEvents.isEmpty()) {
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

    suspend fun Flow<BlockMeta>.toHistoricalStream(startingHeight: Long): Flow<StreamBlockImpl> =
        (startingHeight..getEndingHeight()!!)
            .chunked(options.batchSize)
            .asFlow()
            .doFlatmap { queryBlocks(it).map { b -> b.copy(historical = true) } }
            .filterNonEmptyIfSet()
            .filterByEvents()

    @OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun Flow<Block>.toLiveStream(): Flow<StreamBlockImpl> {

        return channelFlow {
            this@toLiveStream
                .flowOn(dispatchers.io())
                .onStart { log.info("live::starting") }
                .mapNotNull { block: Block ->
                   fetcher.getBlock(block.header?.height!!).toStreamBlock().also {
                       log.debug("live::got block #${it.height}")
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
                .collect { this@channelFlow.send(it) }
        }
    }

    /**
     * Computes and returns the ending height (if it can be determined) tobe used when streaming historical blocks.
     *
     * @return Long? The ending block height to use, if it exists.
     */
    private suspend fun getEndingHeight(): Long? =
        options.toHeight ?: fetcher.getCurrentHeight()


    private fun BlockData.toStreamBlock(): StreamBlockImpl {
        val blockDatetime = block.header?.dateTime()
        val blockEvents = blockResult.blockEvents(blockDatetime)
        val txEvents = blockResult.txEvents(blockDatetime) { index: Int -> block.txHash(index).orEmpty() }
        return StreamBlockImpl(block, blockEvents, txEvents)
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
        val startingHeight: Long? = options.fromHeight
        emitAll(
            if (startingHeight != null) {
                log.info("Listening for live and historical blocks from height $startingHeight")
                merge(
                    streamHistoricalBlocks(),
                    streamLiveBlocks()
                )
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
