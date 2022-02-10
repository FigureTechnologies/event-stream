package io.provenance.eventstream.stream

import arrow.core.Either
import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.config.Options
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.flow.extensions.chunked
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.transformers.queryBlock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flatMapConcat
import mu.KotlinLogging

class HistoricalStream(
    val tendermintServiceClient: TendermintServiceClient,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val options: Options = Options.DEFAULT
) : BlockSource<StreamBlockImpl> {

    private val log = KotlinLogging.logger { }

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
                        async {
                            queryBlock(
                                Either.Left(height),
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

    private fun streamHistoricalBlocks(startHeight: Long, endHeight: Long): Flow<StreamBlockImpl> = flow {
        log.info("historical::streaming blocks from $startHeight to $endHeight")
        log.info("historical::batch size = ${options.batchSize}")

        emitAll(
            MetadataStream(
                startHeight,
                endHeight,
                options.skipIfEmpty,
                options.concurrency,
                options.batchSize,
                tendermintServiceClient
            ).streamBlocks()
        )
    }
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

    private fun <T, R> Flow<List<T>>.doFlatMap(
        ordered: Boolean,
        concurrency: Int,
        block: (List<T>) -> Flow<R>
    ): Flow<R> {
        return if (ordered) {
            flatMapConcat { block(it) }
        } else {
            flatMapMerge(concurrency) { block(it) }
        }
    }

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

    override fun streamBlocks(): Flow<StreamBlockImpl> = streamHistoricalBlocks(startHeight = getStartingHeight()!!)
}
