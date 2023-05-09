package tech.figure.eventstream.stream.flows

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import tech.figure.eventstream.decoder.DecoderAdapter
import tech.figure.eventstream.net.NetAdapter
import tech.figure.eventstream.stream.WebSocketService
import tech.figure.eventstream.stream.clients.BlockData

/**
 * 'from' height argument specifier.
 */
private sealed interface From {
    /**
     * Fetch from the current latest height
     */
    object Latest : From

    /**
     * Fetch from the specified height
     */
    data class Height(val value: Long) : From
}

/**
 * Specify historical poller type.
 */
private sealed interface HistoricalFlow {
    /**
     * No-op flow.
     */
    object NoOp : HistoricalFlow

    /**
     * Custom function.
     */
    data class Custom(val fn: (Long, Long) -> Flow<BlockData>) : HistoricalFlow
}

/**
 * Specify live flow poller type.
 */
private sealed interface LiveFlow {
    /**
     * No-op flow.
     */
    object NoOp : LiveFlow

    /**
     * Uses [pollingBlockDataFlow].
     */
    object Polling : LiveFlow

    /**
     * Uses [wsBlockDataFlow].
     */
    data class Websocket(val decoderAdapter: DecoderAdapter) : LiveFlow

    /**
     * Custom function.
     */
    data class Custom(val fn: () -> Flow<BlockData>) : LiveFlow
}

/**
 * Build a block data flow
 */
class BlockDataFlowBuilder {
    private var from: From = From.Height(1) // earliest
    private var to: Long? = null
    private var historicalFlow: HistoricalFlow = HistoricalFlow.NoOp
    private var liveFlow: LiveFlow = LiveFlow.NoOp
    private var shouldRetry: suspend (Throwable, Long) -> Boolean = shouldRetryFn()

    /**
     * Set the `from` height, i.e. the minimal height the flow will read blocks from.
     *
     * This is a special case of height in that it accepts a single parameter: "latest", which serves as a stand-in
     * for the current latest block height (which is always increasing).
     */
    fun from(height: String): BlockDataFlowBuilder = apply {
        if (height != "latest") {
            throw IllegalArgumentException("Invalid height specifier: $height")
        }
        this.from = From.Latest
    }

    /**
     * Set the `from` height, i.e. the minimal height the flow will read blocks from.
     */
    fun from(height: Long): BlockDataFlowBuilder = apply { this.from = From.Height(height) }

    /**
     * Set the `to` height, i.e. the maximal height the flow will read blocks from.
     *
     * If `null`, no end height is assumed, and the flow is considered open-ended.
     */
    fun to_(height: Long?): BlockDataFlowBuilder = apply { this.to = height }

    /**
     * Read historical (past) block data using a custom function.
     *
     * @param fn The custom historical block reader function.
     */
    fun usingHistoricalFlow(fn: (Long, Long) -> Flow<BlockData>): BlockDataFlowBuilder = apply { this.historicalFlow = HistoricalFlow.Custom(fn) }

    /**
     * Read live block data using a custom function.
     *
     * @param fn The custom live block reader function.
     */
    fun usingLiveFlow(fn: () -> Flow<BlockData>): BlockDataFlowBuilder = apply { this.liveFlow = LiveFlow.Custom(fn) }

    /**
     * Read live blocks using [pollingBlockDataFlow] as the reader function.
     */
    fun usingPollingLiveFlow(): BlockDataFlowBuilder = apply { this.liveFlow = LiveFlow.Polling }

    /**
     * Read live blocks using [wsBlockDataFlow] as the reader function.
     *
     * @param decoderAdapter The decoder adapter used by the reader function.
     */
    fun usingWebsocketLiveFlow(decoderAdapter: DecoderAdapter): BlockDataFlowBuilder = apply { this.liveFlow = LiveFlow.Websocket(decoderAdapter) }

    /**
     * Set the retry logic function.
     *
     * @param retry The retry function.
     */
    fun shouldRetry(retry: suspend (Throwable, Long) -> Boolean): BlockDataFlowBuilder = apply { this.shouldRetry = retry }

    fun build(netAdapter: NetAdapter): Flow<BlockData> {
        val getHeight: suspend () -> Long? = currentHeightFn(netAdapter)
        val currentHeight: Long = runBlocking { getHeight()!! }

        val from: Long = when (from) {
            is From.Latest -> currentHeight
            is From.Height -> (from as From.Height).value
        }
        val historicalFlow: (Long, Long) -> Flow<BlockData> = when (this.historicalFlow) {
            is HistoricalFlow.NoOp -> { _: Long, _: Long -> emptyFlow() }
            is HistoricalFlow.Custom -> (this.historicalFlow as HistoricalFlow.Custom).fn
        }
        val liveFlow: () -> Flow<BlockData> = when (this.liveFlow) {
            is LiveFlow.NoOp -> { { emptyFlow() } }
            is LiveFlow.Custom -> (this.liveFlow as LiveFlow.Custom).fn
            is LiveFlow.Polling -> { { pollingBlockDataFlow(netAdapter) } }
            is LiveFlow.Websocket -> { { wsBlockDataFlow(netAdapter, (this.liveFlow as LiveFlow.Websocket).decoderAdapter, currentHeight = currentHeight) } }
        }

        return combinedFlow(
            getCurrentHeight = currentHeightFn(netAdapter),
            from = from,
            to = to,
            getHeight = blockDataHeightFn,
            historicalFlow = historicalFlow,
            liveFlow = liveFlow,
            shouldRetry = shouldRetry,
        )
    }
}

/**
 * Create a [Flow] of [BlockData] from height to height.
 *
 * Note: This uses RPC polling under the hood for the live stream.
 *
 * This flow will intelligently determine how to merge the live and history flows to
 * create a seamless stream of [BlockData] objects.
 *
 * @param netAdapter The [NetAdapter] to use for network interfacing.
 * @param from The `from` height. If omitted, height 1 is used.
 * @param to The `to` height. If omitted, no end is assumed.
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
    shouldRetry: suspend (Throwable, Long) -> Boolean = shouldRetryFn(),
): Flow<BlockData> = combinedFlow(currentHeightFn(netAdapter), from, to, blockDataHeightFn, historicalFlow, liveFlow, shouldRetry)

/**
 * Create a [Flow] of [BlockData] from height to height. Uses websockets under the hood for the live stream.
 *
 * This flow will intelligently determine how to merge the live and history flows to
 * create a seamless stream of [BlockData] objects.
 *
 * @param netAdapter The [NetAdapter] to use for network interfacing.
 * @param decoderAdapter The [DecoderAdapter] to use to marshal json.
 * @param from The `from` height. If omitted, height 1 is used.
 * @param to The `to` height. If omitted, no end is assumed.
 * @throws IllegalArgumentException If [from] is an invalid value.
 * @return The [Flow] of [BlockData].
 */
fun blockDataFlow(
    netAdapter: NetAdapter,
    decoderAdapter: DecoderAdapter,
    from: Long? = null,
    to: Long? = null,
): Flow<BlockData> {
    val currentHeight: Long = runBlocking { currentHeightFn(netAdapter)()!! }
    return blockDataFlow(
        netAdapter = netAdapter,
        from = from,
        to = to,
        historicalFlow = { f, t -> historicalBlockDataFlow(netAdapter, f, t, currentHeight = currentHeight) },
        liveFlow = { wsBlockDataFlow(netAdapter, decoderAdapter, currentHeight = currentHeight) },
    )
}

/**
 * Create a [Flow] of [BlockData] from height to height. Uses websockets under the hood for the live stream.
 *
 * This flow will intelligently determine how to merge the live and history flows to
 * create a seamless stream of [BlockData] objects.
 *
 * If `"latest"` is supplied for [from], the most current block height value will be used.
 *
 * @param netAdapter The [NetAdapter] to use for network interfacing.
 * @param decoderAdapter The [DecoderAdapter] to use to marshal json.
 * @param from The `from` height. This value must be the string literal `"latest`".
 * @param to The `to` height, if omitted, no end is assumed.
 * @throws IllegalArgumentException If [from] is an invalid value.
 * @return The [Flow] of [BlockData].
 */
fun blockDataFlow(
    netAdapter: NetAdapter,
    decoderAdapter: DecoderAdapter,
    from: String,
    to: Long? = null,
): Flow<BlockData> = BlockDataFlowBuilder()
    .from(from)
    .to_(to)
    .usingWebsocketLiveFlow(decoderAdapter)
    .build(netAdapter)

/**
 * Create a [Flow] of [BlockData] from height to height. Uses websockets under the hood for the live stream.
 *
 * This flow will intelligently determine how to merge the live and history flows to
 * create a seamless stream of [BlockData] objects.
 *
 * If `"latest"` is supplied for [from], the most current block height value will be used.
 *
 * @param netAdapter The [NetAdapter] to use for network interfacing.
 * @param decoderAdapter The [DecoderAdapter] to use to marshal json.
 * @param from The `from` height. This value must be the string literal `"latest`".
 * @param to The `to` height, if omitted, no end is assumed.
 * @throws IllegalArgumentException If [from] is an invalid value.
 * @return The [Flow] of [BlockData].
 */
fun blockDataFlow(
    netAdapter: NetAdapter,
    decoderAdapter: DecoderAdapter,
    wss: WebSocketService,
    from: String,
    to: Long? = null,
): Flow<BlockData> = BlockDataFlowBuilder()
    .from(from)
    .to_(to)
    .usingLiveFlow { wsBlockDataFlow(netAdapter, decoderAdapter, wss = wss) }
    .build(netAdapter)

/**
 * ----
 */
internal val blockDataHeightFn: (BlockData) -> Long = { it.height }
