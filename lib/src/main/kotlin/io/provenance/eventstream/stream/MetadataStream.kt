package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.BlockMeta
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class MetadataStream(
    val fromHeight: Long,
    val toHeight: Long,
    val skipIfEmpty: Boolean,
    val concurrencyLimit: Int,
    val batchSize: Int,
    val tendermintServiceClient: TendermintServiceClient
) {

    private val log = KotlinLogging.logger { }

    fun streamBlocks(): Flow<BlockMeta> {
        return queryMetadata()
    }

    private fun queryMetadata() = flow {
        log.info("metadata::streaming blocks from $fromHeight to $toHeight")
        log.info("metadata::batch size = $batchSize")

        // We're only allowed to query  a block range of (highBlockHeight - lowBlockHeight) = `TENDERMINT_MAX_QUERY_RANGE`
        // max block heights in a single request. If `Options.batchSize` is greater than this value, then we need to
        // make N calls to tendermint to the Tendermint API to have enough blocks to meet batchSize.
        val limit1 = EventStream.TENDERMINT_MAX_QUERY_RANGE.toDouble()
        val limit2 = batchSize.toDouble()
        val numChunks: Int = floor(max(limit1, limit2) / min(limit1, limit2)).toInt()

        emitAll(getBlockHeightQueryRanges(fromHeight, toHeight).chunked(numChunks).asFlow())
    }.flatMapConcat { heightPairChunk: List<Pair<Long, Long>> ->
        val availableBlocks: List<BlockMeta> = coroutineScope {
            heightPairChunk.map { (minHeight, maxHeight) -> async { getBlockHeightsInRange(minHeight, maxHeight) } }
                .awaitAll().flatten()
        }
        log.info("metadata::${availableBlocks.size} block(s) in [${heightPairChunk.minOf { it.first }}..${heightPairChunk.maxOf { it.second }}]")
        availableBlocks.asFlow()
    }

    fun getBlockHeightQueryRanges(minHeight: Long, maxHeight: Long): Sequence<Pair<Long, Long>> {
        if (minHeight > maxHeight) {
            return emptySequence()
        }
        val step = EventStream.TENDERMINT_MAX_QUERY_RANGE
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

    private suspend fun getBlockHeightsInRange(minHeight: Long, maxHeight: Long): List<BlockMeta> {
        if (minHeight > maxHeight) {
            return emptyList()
        }

        // invariant
        assert((maxHeight - minHeight) <= EventStream.TENDERMINT_MAX_QUERY_RANGE) {
            "Difference between (minHeight, maxHeight) can be at maximum ${EventStream.TENDERMINT_MAX_QUERY_RANGE}"
        }

        val blocks = tendermintServiceClient.blockchain(minHeight, maxHeight).result?.blockMetas.let {
            if (skipIfEmpty) {
                it?.filter { it.numTxs ?: 0 > 0 }
            } else {
                it
            }
        }?.mapNotNull { it } ?: emptyList()

        return blocks.sortedBy { it.header?.height }
    }
}