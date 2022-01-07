package io.provenance.eventstream.stream

import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.config.Config
import io.provenance.eventstream.coroutines.DefaultDispatcherProvider
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.stream.clients.BlockFetcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.LoggerFactory

typealias BlockStreamCfg = BlockStreamOptions.() -> BlockStreamOptions

interface BlockStreamFactory {
    fun fromConfig(config: Config) = createSource(
        listOf(
            withBatchSize(config.batch_size),
            withSkipEmptyBlocks(config.skip_empty_blocks),
            withBlockEvents(config.eventStream.filter.blockEvents),
            withTxEvents(config.eventStream.filter.txEvents),
            withFromHeight(config.from),
            withToHeight(config.to),
            withConcurrency(config.eventStream.concurrency),
            withOrdered(config.ordered)
        )
    )

    fun createSource(list: List<BlockStreamCfg>) = createSource(*list.toTypedArray())

    fun createSource(vararg cfg: BlockStreamCfg) = createSource(BlockStreamOptions.create(*cfg))

    fun createSource(options: BlockStreamOptions): BlockSource
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBlockStreamFactory(
    private val config: Config,
    private val decoderEngine: DecoderEngine,
    private val eventStreamBuilder: Scarlet.Builder,
    private val blockFetcher: BlockFetcher,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : BlockStreamFactory {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun createSource(options: BlockStreamOptions): BlockSource {
        log.info("Connecting stream instance to ${config.node}")
        val lifecycle = LifecycleRegistry(config.wsThrottleDurationMs)
        val scarlet: Scarlet = eventStreamBuilder.lifecycle(lifecycle).build()
        val tendermintRpc: TendermintRPCStream = scarlet.create(TendermintRPCStream::class.java)
        val eventStreamService = TendermintEventStreamService(tendermintRpc, lifecycle)

        return EventStream(
            eventStreamService,
            blockFetcher,
            decoderEngine,
            options = options,
            dispatchers = dispatchers
        )
    }
}
