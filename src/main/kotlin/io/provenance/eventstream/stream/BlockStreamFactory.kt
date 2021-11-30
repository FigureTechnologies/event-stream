package io.provenance.eventstream.stream

import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import io.provenance.eventstream.Config
import io.provenance.eventstream.DefaultDispatcherProvider
import io.provenance.eventstream.DispatcherProvider
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.stream.clients.TendermintServiceClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.slf4j.LoggerFactory

typealias BlockStreamCfg = BlockStreamOptions.() -> BlockStreamOptions

interface BlockStreamFactory {
    fun fromConfig(config: Config) = createSource(
        listOf(
            withBatchSize(config.eventStream.batch.size),
            withSkipEmptyBlocks(config.eventStream.skipEmptyBlocks),
            withBlockEvents(config.eventStream.filter.blockEvents),
            withTxEvents(config.eventStream.filter.txEvents),
            withFromHeight(config.eventStream.height.from),
            withToHeight(config.eventStream.height.to),
            withConcurrency(config.eventStream.concurrency),
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
    private val tendermintServiceClient: TendermintServiceClient,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : BlockStreamFactory {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun createSource(options: BlockStreamOptions): BlockSource {
        log.info("Connecting stream instance to ${config.eventStream.websocket.uri}")
        val lifecycle = LifecycleRegistry(config.eventStream.websocket.throttleDurationMs)
        val scarlet: Scarlet = eventStreamBuilder.lifecycle(lifecycle).build()
        val tendermintRpc: TendermintRPCStream = scarlet.create(TendermintRPCStream::class.java)
        val eventStreamService = TendermintEventStreamService(tendermintRpc, lifecycle)

        return EventStream(
            eventStreamService,
            tendermintServiceClient,
            decoderEngine,
            options = options,
            dispatchers = dispatchers
        )
    }
}
