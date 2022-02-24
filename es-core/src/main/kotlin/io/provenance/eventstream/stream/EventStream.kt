package io.provenance.eventstream.stream

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.config.Options
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.extensions.doFlatMap
import io.provenance.eventstream.flow.extensions.chunked
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockMeta
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.transformers.queryBlock
import io.provenance.eventstream.utils.backoff
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.mapNotNull
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
    fun queryBlocks(blockHeights: Iterable<Long>): Flow<StreamBlockImpl> =
        blockHeights.chunked(options.batchSize).asFlow().transform { chunkOfHeights: List<Long> ->
            emitAll(
                coroutineScope {
                    // Concurrently process <batch-size> blocks at a time:
                    chunkOfHeights.map { height ->
                        async {
                            queryBlock(
                                height,
                                skipIfNoTxs = options.skipIfEmpty,
                                historical = true,
                                tendermintServiceClient,
                                options
                            )
                        }
                    }.awaitAll().filterNotNull()
                }.asFlow()
            )
        }.flowOn(dispatchers.io())

    fun streamLiveBlocks(): Flow<StreamBlockImpl> {
        return streamLiveMetaBlocks()
            .toLiveStream()
    }

    fun streamHistoricalBlocks(): Flow<StreamBlockImpl> {
        return streamMetaBlocks()
            .toHistoricalStream()
    }

    fun streamLiveMetaBlocks(): Flow<Block> {
        return LiveMetaDataStream(eventStreamService, tendermintServiceClient, moshi, dispatchers, options).streamBlocks()
    }

    fun streamMetaBlocks(): Flow<BlockMeta> {
        return MetadataStream(options, tendermintServiceClient).streamBlocks()
    }

    @OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun Flow<BlockMeta>.toHistoricalStream(): Flow<StreamBlockImpl> {

        return channelFlow {
            val endHeight: Long = getEndingHeight() ?: error("Couldn't determine ending height")

            this@toHistoricalStream
                .chunked(options.batchSize, endHeight)
                .flowOn(dispatchers.io())
                .transform { blockmetas -> emit(blockmetas.map { it.header!!.height }) }
                .doFlatMap(options.ordered, concurrency = options.concurrency) { queryBlocks(it) }
                .flowOn(dispatchers.io())
                .onCompletion { cause: Throwable? ->
                    if (cause == null) {
                        log.info("historical::exhausted historical block stream ok")
                    } else {
                        log.error("historical::exhausted block stream with error: ${cause.message}")
                    }
                }
                .collect { this@channelFlow.send(it) }
        }
    }

    @OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun Flow<Block>.toLiveStream(): Flow<StreamBlockImpl> {

        return channelFlow {
            this@toLiveStream
                .flowOn(dispatchers.io())
                .onStart { log.info("live::starting") }
                .mapNotNull { block: Block ->
                    val maybeBlock = queryBlock(
                        block.header!!.height,
                        skipIfNoTxs = false,
                        historical = false,
                        tendermintServiceClient,
                        options
                    )
                    if (maybeBlock != null) {
                        log.info("live::got block #${maybeBlock.height}")
                        maybeBlock
                    } else {
                        log.info("live::skipping block #${block.header!!.height}")
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
                .collect { this@channelFlow.send(it) }
        }
    }

    /**
     * Computes and returns the ending height (if it can be determined) tobe used when streaming historical blocks.
     *
     * @return Long? The ending block height to use, if it exists.
     */
    private suspend fun getEndingHeight(): Long? =
        options.toHeight ?: tendermintServiceClient.abciInfo().result?.response?.lastBlockHeight

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
