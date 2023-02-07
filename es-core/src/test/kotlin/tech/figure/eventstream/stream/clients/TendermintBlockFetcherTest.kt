package tech.figure.eventstream.stream.clients

import tech.figure.eventstream.mocks.mockBlockFetcher
import tech.figure.eventstream.test.base.TestBase
import tech.figure.eventstream.utils.MIN_HISTORICAL_BLOCK_HEIGHT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@OptIn(ExperimentalCoroutinesApi::class)
class TendermintBlockFetcherTest : TestBase() {
    private val tendermint = mockBlockFetcher(templates)

    @BeforeAll
    override fun setup() {
        super.setup()
    }

    @AfterAll
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    fun testBlockResponse() {
        val height = MIN_HISTORICAL_BLOCK_HEIGHT

        scopedTest {
            assert(tendermint.getBlock(height).block.header?.height == height)
        }

        assertThrows<Throwable> {
            scopedTest {
                tendermint.getBlock(-1)
            }
        }

        assertThrows<Throwable> {
            scopedTest {
                tendermint.getBlock(999999999)
            }
        }
    }

    @Test
    fun testBlockResultsResponse() {
        val expectedHeight = MIN_HISTORICAL_BLOCK_HEIGHT

        // Expect success:
        dispatcherProvider.runBlockingTest {
            assert(tendermint.getBlockResults(expectedHeight)?.result?.height == expectedHeight)
        }

        // Retrieving a non-existent block should fail (negative case):
        assertThrows<Throwable> {
            dispatcherProvider.runBlockingTest {
                tendermint.getBlockResults(-1)
            }
        }

        // Retrieving a non-existent block should fail (non-existent case):
        assertThrows<Throwable> {
            dispatcherProvider.runBlockingTest {
                tendermint.getBlockResults(999999999)
            }
        }
    }

    @Test
    fun testBlockchainResponse() {
        val expectedMinHeight: Long = MIN_HISTORICAL_BLOCK_HEIGHT
        val expectedMaxHeight: Long = expectedMinHeight + 20 - 1

        scopedTest {
            val blockMetas = tendermint.getBlocksMeta(expectedMinHeight, expectedMaxHeight)

            assert(blockMetas?.size == 20)

            val expectedHeights: Set<Long> = (expectedMinHeight..expectedMaxHeight).toSet()
            val heights: Set<Long> = blockMetas?.mapNotNull { b -> b.header?.height }?.toSet() ?: setOf()

            assert((expectedHeights - heights).isEmpty())
        }

        assertThrows<Throwable> {
            dispatcherProvider.runBlockingTest {
                tendermint.getBlocksMeta(-expectedMinHeight, expectedMaxHeight)
            }
        }
    }
}
