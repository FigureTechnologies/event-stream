package io.provenance.eventstream.stream.producers

import io.provenance.blockchain.stream.api.BlockSource
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.stream.EventStream.Options
import io.provenance.eventstream.stream.TendermintServiceClient
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.TxEvent
import io.provenance.eventstream.stream.models.extensions.blockEvents
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.stream.models.extensions.txEvents
import io.provenance.eventstream.stream.models.extensions.txHash
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import mu.KotlinLogging
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

interface BlockFetcher {
    /**
     * Query a block by height, returning any events associated with the block.
     *
     *  @param height Fetch a block, plus its events, by its height.
     *  be returned in its place.
     */
    suspend fun queryBlock(height: Long): StreamBlock?
}

class TMBlockFetcher(private val tm: TendermintServiceClient) : BlockFetcher {
    override suspend fun queryBlock(height: Long): StreamBlock? {
        val block: Block? = tm.block(height).result?.block

        return block?.run {
            val blockDatetime = header?.dateTime()
            val blockResponse = tm.blockResults(header?.height).result
            val blockEvents: List<BlockEvent> = blockResponse.blockEvents(blockDatetime)
            val txEvents: List<TxEvent> = blockResponse.txEvents(blockDatetime) { index: Int -> txHash(index) ?: "" }
            val streamBlock = StreamBlock(this, blockEvents, txEvents)
            streamBlock
        }
    }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HistoricalBlockSource(
    private val dispatchers: DispatcherProvider,
    private val blockFetcher: BlockFetcher,
    private val tm: TendermintServiceClient,
    private val startHeight: Long,
    private val endHeight: Long?,
    private val batchSize: Int = 16,
    private val skipIfEmpty: Boolean = true,
    private val concurrency: Int = DEFAULT_CONCURRENCY,
) : BlockSource {
    private val log = KotlinLogging.logger {}

    companion object {
        /**
         * The maximum size of the query range for block heights allowed by the Tendermint API.
         * This means, for a given block height `H`, we can ask for blocks in the range [`H`, `H` + `TENDERMINT_MAX_QUERY_RANGE`].
         * Requesting a larger range will result in the API emitting an error.
         */
        const val TENDERMINT_MAX_QUERY_RANGE = 20
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
        blockHeights.chunked(batchSize).asFlow().transform { chunkOfHeights: List<Long> ->
            emitAll(
                coroutineScope {
                    // Concurrently process <batch-size> blocks at a time:
                    chunkOfHeights.map { height -> async { blockFetcher.queryBlock(height) } }.awaitAll().filterNotNull()
                }.asFlow()
            )
        }.flowOn(dispatchers.io())


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
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getBlockHeightsInRange(minHeight: Long, maxHeight: Long): List<Long> {
        if (minHeight > maxHeight) {
            return emptyList()
        }

        // invariant
        assert((maxHeight - minHeight) <= TENDERMINT_MAX_QUERY_RANGE) {
            "Difference between (minHeight, maxHeight) can be at maximum $TENDERMINT_MAX_QUERY_RANGE"
        }

        val blocks = tm.blockchain(minHeight, maxHeight).result?.blockMetas.let {
            if (skipIfEmpty) {
                it?.filter { (it.numTxs ?: 0) > 0 }
            } else {
                it
            }
        }?.mapNotNull { it.header?.height } ?: emptyList()

        return blocks.sortedWith(naturalOrder())
    }

    override fun streamBlocks(): Flow<StreamBlock> = flow {
        log.info("historical::streaming blocks from $startHeight to $endHeight")
        log.info("historical::batch size = $batchSize")

        // We're only allowed to query  a block range of (highBlockHeight - lowBlockHeight) = `TENDERMINT_MAX_QUERY_RANGE`
        // max block heights in a single request. If `Options.batchSize` is greater than this value, then we need to
        // make N calls to tendermint to the Tendermint API to have enough blocks to meet batchSize.
        val limit1 = TENDERMINT_MAX_QUERY_RANGE.toDouble()
        val limit2 = batchSize.toDouble()
        val numChunks: Int = floor(max(limit1, limit2) / min(limit1, limit2)).toInt()

        emitAll(getBlockHeightQueryRanges(startHeight, endHeight!!).chunked(numChunks).asFlow())
    }.map { heightPairChunk: List<Pair<Long, Long>> ->
        val availableBlocks: List<Long> = coroutineScope {
            heightPairChunk.map { (minHeight, maxHeight) -> async { getBlockHeightsInRange(minHeight, maxHeight) } }
                .awaitAll().flatten()
        }
        log.info("historical::${availableBlocks.size} block(s) in [${heightPairChunk.minOf { it.first }}..${heightPairChunk.maxOf { it.second }}]")
        availableBlocks
    }.flowOn(dispatchers.io()).flatMapMerge(concurrency) { queryBlocks(it) }.flowOn(dispatchers.io())
        .map { it.copy(historical = true) }.onCompletion { cause: Throwable? ->
            if (cause == null) {
                log.info("historical::exhausted historical block stream ok")
            } else {
                log.error("historical::exhausted block stream with error: ${cause.message}")
            }
        }


}