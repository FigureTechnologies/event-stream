package io.provenance.eventstream.mocks

import com.tinder.scarlet.Message
import com.tinder.scarlet.ShutdownReason
import com.tinder.scarlet.Stream
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.utils.toStream
import io.provenance.eventstream.WsAdapter
import io.provenance.eventstream.coroutines.DispatcherProvider
import io.provenance.eventstream.net.NetAdapter
import io.provenance.eventstream.net.netAdapter
import io.provenance.eventstream.stream.WebSocketService
import io.provenance.eventstream.stream.clients.BlockData
import io.provenance.eventstream.stream.clients.BlockFetcher
import io.provenance.eventstream.stream.models.ABCIInfoResponse
import io.provenance.eventstream.stream.models.BlockMeta
import io.provenance.eventstream.stream.models.BlockResponse
import io.provenance.eventstream.stream.models.BlockResultsResponse
import io.provenance.eventstream.stream.models.BlockchainResponse
import io.provenance.eventstream.test.mocks.MockEventStreamService
import io.provenance.eventstream.test.utils.MAX_LIVE_BLOCK_HEIGHT
import io.provenance.eventstream.test.utils.MIN_LIVE_BLOCK_HEIGHT
import io.provenance.eventstream.test.utils.Template
import io.reactivex.Flowable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

suspend fun mockEventStreamService(
    template: Template,
    dispatcherProvider: DispatcherProvider,
    ranges: List<LongRange> = listOf(MIN_LIVE_BLOCK_HEIGHT..MAX_LIVE_BLOCK_HEIGHT),
    init: MockEventStreamService.Builder.() -> Unit = {},
): WebSocketService = MockEventStreamService.Builder().apply {
    ranges.forEach { range ->
        range.forEach { height ->
            response(template.read("live/$height.json"))
        }
    }
}.dispatchers(dispatcherProvider).also(init).build()

fun mockWsAdapter(template: Template): WsAdapter {
    return object : WsAdapter {
        val closed = AtomicBoolean(true)
        val empty = listOf(WebSocket.Event.OnMessageReceived(Message.Text("")))

        val responses = template.readAll("live").toList().map {
            WebSocket.Event.OnMessageReceived(Message.Text(it))
        }

        val data get() = Flowable.fromIterable(empty + responses)

        override fun invoke(): WebSocket {
            return object : WebSocket {
                override fun cancel() {
                    closed.set(true)
                }

                override fun close(shutdownReason: ShutdownReason): Boolean {
                    closed.set(true)
                    return true
                }

                override fun open(): Stream<WebSocket.Event> {
                    closed.set(false)
                    return data.toStream() as Stream<WebSocket.Event>
                }

                override fun send(message: Message): Boolean {
                    TODO("Not yet implemented")
                }
            }
        }
    }
}

fun mockBlockFetcher(template: Template): BlockFetcher {
    return object : BlockFetcher {
        override suspend fun getBlocksMeta(min: Long, max: Long): List<BlockMeta>? {
            val response = template.readAs(BlockchainResponse::class.java, "blockchain/$min-$max.json")
            return response?.result?.blockMetas
        }

        override suspend fun getCurrentHeight(): Long {
            val response = template.readAs(ABCIInfoResponse::class.java, "abci_info/success.json")
            return response!!.result!!.response.lastBlockHeight!!
        }

        override suspend fun getBlock(height: Long): BlockData {
            val block = template.readAs(BlockResponse::class.java, "block/$height.json")
            val results = template.readAs(BlockResultsResponse::class.java, "block_results/$height.json")
            return BlockData(block!!.result!!.block!!, results!!.result)
        }

        override suspend fun getBlockResults(height: Long): BlockResultsResponse? {
            return template.readAs(BlockResultsResponse::class.java, "block_results/$height.json")
        }

        override suspend fun getBlocks(heights: List<Long>, concurrency: Int, context: CoroutineContext): Flow<BlockData> {
            return heights.map { getBlock(it) }.asFlow()
        }
    }
}

fun mockNetAdapter(template: Template): NetAdapter {
    return netAdapter(mockWsAdapter(template), mockBlockFetcher(template))
}
