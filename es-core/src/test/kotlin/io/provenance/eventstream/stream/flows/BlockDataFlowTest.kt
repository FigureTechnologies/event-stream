package io.provenance.eventstream.stream.flows

import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.mocks.mockEventStreamService
import io.provenance.eventstream.mocks.mockNetAdapter
import io.provenance.eventstream.stream.WebSocketService
import io.provenance.eventstream.stream.clients.BlockData
import io.provenance.eventstream.stream.models.BlockHeader
import io.provenance.eventstream.test.base.TestBase
import io.provenance.eventstream.test.utils.EXPECTED_LIVE_TOTAL_BLOCK_COUNT
import io.provenance.eventstream.test.utils.EXPECTED_NONEMPTY_BLOCKS
import io.provenance.eventstream.test.utils.EXPECTED_TOTAL_BLOCKS
import io.provenance.eventstream.test.utils.MAX_HISTORICAL_BLOCK_HEIGHT
import io.provenance.eventstream.test.utils.MAX_LIVE_BLOCK_HEIGHT
import io.provenance.eventstream.test.utils.MIN_HISTORICAL_BLOCK_HEIGHT
import io.provenance.eventstream.test.utils.MIN_LIVE_BLOCK_HEIGHT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(ExperimentalCoroutinesApi::class)
class BlockDataFlowTest : TestBase() {
    private val netAdapter = mockNetAdapter(templates)

    @BeforeAll
    override fun setup() {
        super.setup()
    }

    @AfterAll
    override fun tearDown() {
        super.tearDown()
    }

    fun mockBlockDataFlow(wss: WebSocketService): Flow<BlockData> {
        val wsFlow = wsBlockDataFlow(netAdapter, moshiDecoderAdapter(), wss = wss)
        val hFlow = historicalBlockDataFlow(netAdapter, MIN_HISTORICAL_BLOCK_HEIGHT, MAX_HISTORICAL_BLOCK_HEIGHT)
        return blockDataFlow(
            netAdapter,
            MIN_HISTORICAL_BLOCK_HEIGHT,
            MAX_LIVE_BLOCK_HEIGHT,
            { _, _ -> hFlow },
            { wsFlow }
        )
    }

    fun mockBlockHeaderFlow(wss: WebSocketService): Flow<BlockHeader> {
        val wsFlow = wsBlockHeaderFlow(netAdapter, moshiDecoderAdapter(), wss = wss)
        val hFlow = historicalBlockHeaderFlow(netAdapter, MIN_HISTORICAL_BLOCK_HEIGHT, MAX_HISTORICAL_BLOCK_HEIGHT)
        return blockHeaderFlow(
            netAdapter,
            MIN_HISTORICAL_BLOCK_HEIGHT,
            MAX_LIVE_BLOCK_HEIGHT,
            { _, _ -> hFlow },
            { wsFlow }
        )
    }

    @Nested
    inner class Historical {
        @Test
        fun testBlockFlow() {
            scopedTest {
                val fetched = historicalBlockDataFlow(
                    netAdapter = netAdapter,
                    from = MIN_HISTORICAL_BLOCK_HEIGHT,
                    to = MAX_HISTORICAL_BLOCK_HEIGHT,
                ).toList()

                assert(fetched.size.toLong() == EXPECTED_TOTAL_BLOCKS)
            }

            scopedTest {
                val fetched = historicalBlockHeaderFlow(
                    netAdapter = netAdapter,
                    from = MIN_HISTORICAL_BLOCK_HEIGHT,
                    to = MAX_HISTORICAL_BLOCK_HEIGHT,
                ).toList()

                assert(fetched.size.toLong() == EXPECTED_TOTAL_BLOCKS)
            }
        }

        @Test
        fun testBlockFiltering() {
            scopedTest {
                // If skipping empty blocks, we should get EXPECTED_NONEMPTY_BLOCKS:
                val collectedSkip = historicalBlockDataFlow(
                    netAdapter,
                    MIN_HISTORICAL_BLOCK_HEIGHT,
                    MAX_HISTORICAL_BLOCK_HEIGHT
                ).filter { (it.block.data?.txs.orEmpty()).isNotEmpty() }.toList()

                assert(collectedSkip.size.toLong() == EXPECTED_NONEMPTY_BLOCKS)
            }

            scopedTest {
                // If skipping empty blocks, we should get EXPECTED_NONEMPTY_BLOCKS:
                val collectedSkip = historicalBlockHeaderFlow(
                    netAdapter,
                    MIN_HISTORICAL_BLOCK_HEIGHT,
                    MAX_HISTORICAL_BLOCK_HEIGHT
                ).filter { it.height == MIN_HISTORICAL_BLOCK_HEIGHT }.toList()

                assert(collectedSkip.size == 1)
            }
        }

        @Test
        fun testBlockMeta() {
            scopedTest {
                // If not skipping empty blocks, we should get EXPECTED_TOTAL_BLOCKS:
                val collectedNoSkip = historicalBlockMetaFlow(
                    netAdapter,
                    MIN_HISTORICAL_BLOCK_HEIGHT,
                    MAX_HISTORICAL_BLOCK_HEIGHT
                ).toList()

                assert(collectedNoSkip.size.toLong() == EXPECTED_TOTAL_BLOCKS)

                // If skipping empty blocks, we should get EXPECTED_NONEMPTY_BLOCKS:
                val collectedSkip = historicalBlockMetaFlow(
                    netAdapter,
                    MIN_HISTORICAL_BLOCK_HEIGHT,
                    MAX_HISTORICAL_BLOCK_HEIGHT
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
                val collected = wsBlockDataFlow(netAdapter, moshiDecoderAdapter(), wss = service).toList()
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
                val collected = wsBlockDataFlow(netAdapter, moshiDecoderAdapter(), wss = service).toList()
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

                    wsBlockDataFlow(netAdapter, moshiDecoderAdapter(), wss = service).toList()
                }
            }
        }
    }

    @Nested
    inner class Combo {
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
    }
}
