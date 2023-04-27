package tech.figure.eventstream

import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import tech.figure.blockchain.stream.api.BlockSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.LoggerFactory
import tech.figure.eventstream.adapter.json.decoder.DecoderEngine
import tech.figure.eventstream.config.Config
import tech.figure.eventstream.coroutines.DefaultDispatcherProvider
import tech.figure.eventstream.coroutines.DispatcherProvider
import tech.figure.eventstream.stream.WebSocketChannel
import tech.figure.eventstream.stream.withLifecycle
import tech.figure.eventstream.stream.EventStream
import tech.figure.eventstream.stream.clients.TendermintBlockFetcher
import tech.figure.eventstream.stream.models.StreamBlockImpl

typealias BlockStreamCfg = BlockStreamOptions.() -> BlockStreamOptions

interface BlockStreamFactory {
    fun fromConfig(config: Config) = createSource(
        listOf(
            withBatchSize(config.eventStream.batch.size),
            withSkipEmptyBlocks(config.eventStream.skipEmptyBlocks),
            withBlockEvents(config.eventStream.filter.blockEvents),
            withTxEvents(config.eventStream.filter.txEvents),
            withFromHeight(config.from),
            withToHeight(config.to),
            withConcurrency(config.eventStream.concurrency),
            withOrdered(config.ordered),
        ),
    )

    fun createSource(list: List<BlockStreamCfg>) = createSource(*list.toTypedArray())

    fun createSource(vararg cfg: BlockStreamCfg) = createSource(BlockStreamOptions.create(*cfg))

    fun createSource(options: BlockStreamOptions): BlockSource<StreamBlockImpl>
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBlockStreamFactory(
    private val config: Config,
    private val decoderEngine: DecoderEngine,
    private val eventStreamBuilder: Scarlet.Builder,
    private val blockFetcher: TendermintBlockFetcher,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : BlockStreamFactory {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun createSource(options: BlockStreamOptions): BlockSource<StreamBlockImpl> {
        log.info("Connecting stream instance to ${config.node}")
        val lifecycle = LifecycleRegistry(config.eventStream.websocket.throttleDurationMs)
        val scarlet: Scarlet = eventStreamBuilder.lifecycle(lifecycle).build()
        val channel: WebSocketChannel = scarlet.create(WebSocketChannel::class.java)
        val eventStreamService = channel.withLifecycle(lifecycle)

        return EventStream(
            eventStreamService,
            blockFetcher,
            decoderEngine,
            options = options,
            dispatchers = dispatchers,
        )
    }
}
