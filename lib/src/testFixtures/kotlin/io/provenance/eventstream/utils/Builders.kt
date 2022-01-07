package io.provenance.eventstream.test.utils

import com.squareup.moshi.Moshi
import io.provenance.eventstream.adapter.json.decoder.MoshiDecoderEngine
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.stream.*
import io.provenance.eventstream.stream.clients.TendermintBlockFetcher
import io.provenance.eventstream.stream.models.ABCIInfoResponse
import io.provenance.eventstream.stream.models.BlockResponse
import io.provenance.eventstream.stream.models.BlockResultsResponse
import io.provenance.eventstream.stream.models.BlockchainResponse
import io.provenance.eventstream.test.mocks.MockEventStreamService
import io.provenance.eventstream.test.mocks.MockTendermintServiceClient
import io.provenance.eventstream.test.mocks.ServiceMocker
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
object Builders {

    /**
     * Create a mock of the Tendermint service API exposed on Provenance.
     */
    fun tendermintService(): ServiceMocker.Builder = ServiceMocker.Builder()
        .doFor("abciInfo") {
            Defaults.templates.readAs(
                ABCIInfoResponse::class.java,
                "abci_info/success.json",
                mapOf("last_block_height" to MAX_HISTORICAL_BLOCK_HEIGHT)
            )
        }
        .doFor("block") { Defaults.templates.readAs(BlockResponse::class.java, "block/${it[0]}.json") }
        .doFor("blockResults") {
            Defaults.templates.readAs(
                BlockResultsResponse::class.java,
                "block_results/${it[0]}.json"
            )
        }
        .doFor("blockchain") {
            Defaults.templates.readAs(
                BlockchainResponse::class.java,
                "blockchain/${it[0]}-${it[1]}.json"
            )
        }

    /**
     * Create a mock of the Tendermint RPC event stream exposed on Provenance.
     */
    fun eventStreamService(includeLiveBlocks: Boolean = true): MockEventStreamService.Builder {
        val serviceBuilder = MockEventStreamService.Builder()
        if (includeLiveBlocks) {
            for (liveBlockResponse in Defaults.templates.readAll("live")) {
                serviceBuilder.response(liveBlockResponse)
            }
        }
        return serviceBuilder
    }

    /**
     * Create a mock of the Provenance block event stream.
     */
    data class EventStreamBuilder(val builders: Builders) {
        var dispatchers: DispatcherProvider? = null
        var eventStreamService: EventStreamService? = null
        var moshi: Moshi? = null
        var options: BlockStreamOptions = BlockStreamOptions()
        var includeLiveBlocks: Boolean = true

        fun <T : EventStreamService> eventStreamService(value: T) = apply { eventStreamService = value }
        fun moshi(value: Moshi) = apply { moshi = value }
        fun dispatchers(value: DispatcherProvider) = apply { dispatchers = value }
        fun options(value: BlockStreamOptions) = apply { options = value }
        fun includeLiveBlocks(value: Boolean) = apply { includeLiveBlocks = value }

        // shortcuts for options:
        fun batchSize(value: Int) = apply { options = options.copy(batchSize = value) }
        fun skipEmptyBlocks(value: Boolean) = apply { options = options.copy(skipEmptyBlocks = value) }
        fun matchBlockEvents(events: Set<String>) = apply { options = options.copy(blockEvents = events) }
        fun matchTxEvents(events: Set<String>) = apply { options = options.copy(txEvents = events) }

        suspend fun build(): EventStream {
            val dispatchers = dispatchers ?: error("dispatchers must be provided")
            return EventStream(
                eventStreamService = eventStreamService
                    ?: eventStreamService(includeLiveBlocks = includeLiveBlocks)
                        .dispatchers(dispatchers)
                        .build(),
                decoder = MoshiDecoderEngine(moshi!!),
                dispatchers = dispatchers,
                options = options,
                blockFetcher = TendermintBlockFetcher(""),
                checkpoint = InMemoryCheckpoint()
            )
        }
    }

    fun eventStream(): EventStreamBuilder = EventStreamBuilder(this)
}