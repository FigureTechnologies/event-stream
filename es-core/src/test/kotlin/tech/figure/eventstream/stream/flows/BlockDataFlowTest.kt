package tech.figure.eventstream.stream.flows

import tech.figure.eventstream.decoder.moshiDecoderAdapter
import tech.figure.eventstream.mocks.mockEventStreamService
import tech.figure.eventstream.mocks.mockNetAdapter
import tech.figure.eventstream.stream.WebSocketService
import tech.figure.eventstream.stream.clients.BlockData
import tech.figure.eventstream.stream.models.BlockHeader
import tech.figure.eventstream.test.base.TestBase
import tech.figure.eventstream.utils.EXPECTED_LIVE_TOTAL_BLOCK_COUNT
import tech.figure.eventstream.utils.EXPECTED_NONEMPTY_BLOCKS
import tech.figure.eventstream.utils.EXPECTED_HISTORICAL_BLOCK_COUNT
import tech.figure.eventstream.utils.MAX_HISTORICAL_BLOCK_HEIGHT
import tech.figure.eventstream.utils.MAX_LIVE_BLOCK_HEIGHT
import tech.figure.eventstream.utils.MIN_HISTORICAL_BLOCK_HEIGHT
import tech.figure.eventstream.utils.MIN_LIVE_BLOCK_HEIGHT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(ExperimentalCoroutinesApi::class)
class BlockDataFlowTest : TestBase() {
    @BeforeAll
    override fun setup() {
        super.setup()
    }

    @AfterAll
    override fun tearDown() {
        super.tearDown()
    }

    fun mockBlockDataFlow(wss: WebSocketService): Flow<BlockData> {
        val netAdapter = mockNetAdapter(templates)
        val wsFlow = wsBlockDataFlow(netAdapter, moshiDecoderAdapter(), wss = wss)
        val hFlow = historicalBlockDataFlow(netAdapter, MIN_HISTORICAL_BLOCK_HEIGHT, MAX_HISTORICAL_BLOCK_HEIGHT)
        return blockDataFlow(
            netAdapter,
            MIN_HISTORICAL_BLOCK_HEIGHT,
            MAX_LIVE_BLOCK_HEIGHT,
            { _, _ -> hFlow },
            { wsFlow },
        )
    }

    fun mockBlockHeaderFlow(wss: WebSocketService): Flow<BlockHeader> {
        val netAdapter = mockNetAdapter(templates)
        val wsFlow = wsBlockHeaderFlow(netAdapter, moshiDecoderAdapter(), wss = wss)
        val hFlow = historicalBlockHeaderFlow(netAdapter, MIN_HISTORICAL_BLOCK_HEIGHT, MAX_HISTORICAL_BLOCK_HEIGHT)
        return blockHeaderFlow(
            netAdapter,
            MIN_HISTORICAL_BLOCK_HEIGHT,
            MAX_LIVE_BLOCK_HEIGHT,
            { _, _ -> hFlow },
            { wsFlow },
        )
    }

    @Nested
    inner class Historical {
        @Test
        fun testBlockFlow() {
            scopedTest {
                val fetched = historicalBlockDataFlow(
                    netAdapter = mockNetAdapter(templates),
                    from = MIN_HISTORICAL_BLOCK_HEIGHT,
                    to = MAX_HISTORICAL_BLOCK_HEIGHT,
                ).toList()

                assert(fetched.size.toLong() == EXPECTED_HISTORICAL_BLOCK_COUNT)
            }

            scopedTest {
                val fetched = historicalBlockHeaderFlow(
                    netAdapter = mockNetAdapter(templates),
                    from = MIN_HISTORICAL_BLOCK_HEIGHT,
                    to = MAX_HISTORICAL_BLOCK_HEIGHT,
                ).toList()

                assert(fetched.size.toLong() == EXPECTED_HISTORICAL_BLOCK_COUNT)
            }
        }

        @Test
        fun testBlockFiltering() {
            scopedTest {
                // If skipping empty blocks, we should get EXPECTED_NONEMPTY_BLOCKS:
                val collectedSkip = historicalBlockDataFlow(
                    mockNetAdapter(templates),
                    MIN_HISTORICAL_BLOCK_HEIGHT,
                    MAX_HISTORICAL_BLOCK_HEIGHT,
                ).filter { (it.block.data?.txs.orEmpty()).isNotEmpty() }.toList()

                assert(collectedSkip.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
            }

            scopedTest {
                // If skipping empty blocks, we should get EXPECTED_NONEMPTY_BLOCKS:
                val collectedSkip = historicalBlockHeaderFlow(
                    mockNetAdapter(templates),
                    MIN_HISTORICAL_BLOCK_HEIGHT,
                    MAX_HISTORICAL_BLOCK_HEIGHT,
                ).filter { it.height == MIN_HISTORICAL_BLOCK_HEIGHT }.toList()

                assert(collectedSkip.size == 1)
            }
        }

        @Test
        fun testBlockMeta() {
            scopedTest {
                // If not skipping empty blocks, we should get EXPECTED_TOTAL_BLOCKS:
                val collectedNoSkip = historicalBlockMetaFlow(
                    mockNetAdapter(templates),
                    MIN_HISTORICAL_BLOCK_HEIGHT,
                    MAX_HISTORICAL_BLOCK_HEIGHT,
                ).toList()

                assert(collectedNoSkip.size.toLong() == EXPECTED_HISTORICAL_BLOCK_COUNT)

                // If skipping empty blocks, we should get EXPECTED_NONEMPTY_BLOCKS:
                val collectedSkip = historicalBlockMetaFlow(
                    mockNetAdapter(templates),
                    MIN_HISTORICAL_BLOCK_HEIGHT,
                    MAX_HISTORICAL_BLOCK_HEIGHT,
                ).filter { (it.numTxs ?: 0) > 0 }.toList()

                assert(collectedSkip.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
                assert(collectedSkip.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
            }
        }
    }

    @Nested
    inner class WebSocket {
        @Test
        fun testBlockStreaming() {
            scopedTest {
                val service = mockEventStreamService(templates, dispatcherProvider)
                val collected = wsBlockDataFlow(mockNetAdapter(templates), moshiDecoderAdapter(), wss = service).toList()
                assert(collected.size.toLong() == EXPECTED_LIVE_TOTAL_BLOCK_COUNT) {
                    "collected:${collected.size} expected:$EXPECTED_LIVE_TOTAL_BLOCK_COUNT"
                }
            }
        }

        @Test
        fun testBlockStreamingMissedBlocksCatchUp() {
            scopedTest {
                // Range of ranges, first block, last block, nothing more.
                val ranges = listOf(
                    MIN_LIVE_BLOCK_HEIGHT..MIN_LIVE_BLOCK_HEIGHT,
                    MAX_LIVE_BLOCK_HEIGHT..MAX_LIVE_BLOCK_HEIGHT,
                )

                val service = mockEventStreamService(templates, dispatcherProvider, ranges)
                val collected = wsBlockDataFlow(mockNetAdapter(templates), moshiDecoderAdapter(), wss = service).toList()
                assert(collected.size.toLong() == EXPECTED_LIVE_TOTAL_BLOCK_COUNT) {
                    "collected:${collected.size} expected:$EXPECTED_LIVE_TOTAL_BLOCK_COUNT"
                }
            }
        }

        @Test
        fun testBlockStreamCancelOnPanic() {
            assertThrows<CancellationException> {
                scopedTest {
                    val service = mockEventStreamService(templates, dispatcherProvider) {
                        response(templates.read("rpc/responses/panic.json"))
                    }

                    wsBlockDataFlow(mockNetAdapter(templates), moshiDecoderAdapter(), wss = service).toList()
                }
            }
        }
    }

    @Nested
    inner class Combo {
        @Test
        fun testBlockStreamingHistoricAndLive() = scopedTest {
            val service = mockEventStreamService(templates, dispatcherProvider)
            scopedTest {
                val collected = mockBlockDataFlow(service).toList()
                assert(collected.size.toLong() == EXPECTED_LIVE_TOTAL_BLOCK_COUNT + EXPECTED_HISTORICAL_BLOCK_COUNT)
            }
        }

        @Test
        fun testBlockStreamGoodBlocksThenPanic() = scopedTest {
            val service = mockEventStreamService(templates, dispatcherProvider) {
                response(templates.read("rpc/responses/panic.json"))
            }

            assertThrows<CancellationException> {
                scopedTest {
                    mockBlockDataFlow(service).collect()
                }
            }
        }

        @Test
        fun testBlockStreamImmediatePanic() = scopedTest {
            val service = mockEventStreamService(templates, dispatcherProvider, emptyList()) {
                response(templates.read("rpc/responses/panic.json"))
            }

            assertThrows<CancellationException> {
                scopedTest {
                    mockBlockDataFlow(service).collect()
                }
            }
        }

        @Test
        fun testBlockStreamingWithLatestHeight() = scopedTest {
            val currentHeight = MIN_LIVE_BLOCK_HEIGHT
            val ranges = listOf(MIN_LIVE_BLOCK_HEIGHT..MAX_LIVE_BLOCK_HEIGHT)
            val netAdapter = mockNetAdapter(templates, currentHeight = currentHeight)
            val service = mockEventStreamService(templates, dispatcherProvider, ranges)

            // Needed, since live block streams aren't supposed to terminate:
            val collected = mutableListOf<BlockData>()

            // we want to collect as many blocks as we can in the timeout window. `withTimeoutOrNull()` will return
            // null upon the timeout occurring, which is not what we want. As a result, it's necessary to explicitly
            // collect the received blocks in `collected`.
            withTimeoutOrNull(2_000L) {
                blockDataFlow(netAdapter = netAdapter, decoderAdapter = moshiDecoderAdapter(), wss = service, from = "latest")
                    .collect {
                        collected.add(it)
                    }
            }

            assert(collected.size.toLong() == EXPECTED_LIVE_TOTAL_BLOCK_COUNT)
        }
    }
}
