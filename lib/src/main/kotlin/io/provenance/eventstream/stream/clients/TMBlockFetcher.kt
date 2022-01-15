package io.provenance.eventstream.stream.clients

import io.provenance.eventstream.stream.TendermintServiceClient
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.TxEvent
import io.provenance.eventstream.stream.models.extensions.blockEvents
import io.provenance.eventstream.stream.models.extensions.dateTime
import io.provenance.eventstream.stream.models.extensions.txEvents
import io.provenance.eventstream.stream.models.extensions.txHash
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.transform

/**
 *
 */
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

    override fun queryBlocks(blockHeights: Iterable<Long>, batchSize: Int): Flow<StreamBlock> =
        blockHeights.chunked(batchSize).asFlow().transform { chunkOfHeights: List<Long> ->
            emitAll(
                coroutineScope {
                    // Concurrently process <batch-size> blocks at a time:
                    chunkOfHeights.map { height -> async { queryBlock(height) } }.awaitAll().filterNotNull()
                }.asFlow()
            )
        }
}
