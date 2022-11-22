package tech.figure.eventstream.stream.flows

import kotlinx.coroutines.flow.Flow
import tech.figure.eventstream.decoder.DecoderAdapter
import tech.figure.eventstream.net.NetAdapter
import tech.figure.eventstream.stream.clients.BlockData

/**
 * Create a [Flow] of [BlockData] from height to height. Use rpc polling under the hood for the live stream.
 *
 * This flow will intelligently determine how to merge the live and history flows to
 * create a seamless stream of [BlockData] objects.
 *
 * @param netAdapter The [NetAdapter] to use for network interfacing.
 * @param from The `from` height, if omitted, height 1 is used.
 * @param to The `to` height, if omitted, no end is assumed.
 * @param historicalFlow The historical flow data generator to use (default: [historicalBlockDataFlow])
 * @param liveFlow The live flow data generator to use (default: [pollingBlockDataFlow])
 * @return The [Flow] of [BlockData].
 */
fun blockDataFlow(
    netAdapter: NetAdapter,
    from: Long? = null,
    to: Long? = null,
    historicalFlow: (Long, Long) -> Flow<BlockData> = { f, t -> historicalBlockDataFlow(netAdapter, f, t) },
    liveFlow: () -> Flow<BlockData> = { pollingBlockDataFlow(netAdapter) },
): Flow<BlockData> = combinedFlow(currentHeightFn(netAdapter), from, to, blockDataHeightFn, historicalFlow, liveFlow)

/**
 * Create a [Flow] of [BlockData] from height to height. Uses websockets under the hood for the live stream.
 *
 * This flow will intelligently determine how to merge the live and history flows to
 * create a seamless stream of [BlockData] objects.
 *
 * @param netAdapter The [NetAdapter] to use for network interfacing.
 * @param decoderAdapter The [DecoderAdapter] to use to marshal json.
 * @param from The `from` height, if omitted, height 1 is used.
 * @param to The `to` height, if omitted, no end is assumed.
 * @return The [Flow] of [BlockData].
 */
fun blockDataFlow(
    netAdapter: NetAdapter,
    decoderAdapter: DecoderAdapter,
    from: Long? = null,
    to: Long? = null,
    currentHeight: Long
): Flow<BlockData> = blockDataFlow(
    netAdapter,
    from,
    to,
    historicalFlow = { f, t -> historicalBlockDataFlow(netAdapter, f, t, currentHeight = currentHeight) },
    liveFlow = { wsBlockDataFlow(netAdapter, decoderAdapter, currentHeight = currentHeight) },
)

/**
 * ----
 */
internal val blockDataHeightFn: (BlockData) -> Long = { it.height }
