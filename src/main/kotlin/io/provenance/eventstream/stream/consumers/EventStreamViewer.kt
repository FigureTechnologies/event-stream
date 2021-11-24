package io.provenance.eventstream.stream.consumers

import io.provenance.eventstream.flow.extensions.cancelOnSignal
import io.provenance.eventstream.logger
import io.provenance.eventstream.observeBlock
import io.provenance.eventstream.stream.EventStream
import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runInterruptible
import java.io.Closeable

/**
 * An event stream consumer that displays blocks from the provided event stream.
 *
 * @property eventStream The event stream which provides blocks to this consumer.
 * @property options Options used to configure this consumer.
 */
//@OptIn(FlowPreview::class)
//@ExperimentalCoroutinesApi
//class EventStreamViewer(private val eventStream: EventStream) {
//    constructor(
//        eventStreamFactory: EventStream.Factory,
//        options: EventStream.Options = EventStream.Options.DEFAULT
//    ) : this(eventStreamFactory.create(options))
//
//    private val log = logger()
//
//    private fun onError(error: Throwable) {
//        log.error("", error)
//    }
//
//    suspend fun consume(error: (Throwable) -> Unit = ::onError, ok: (block: StreamBlock) -> Unit) {
//        consume(error) { b, _ -> ok(b) }
//    }
//
//    suspend fun consume(
//        error: (Throwable) -> Unit = ::onError,
//        ok: (block: StreamBlock, serialize: (StreamBlock) -> String) -> Unit
//    ) {
//        eventStream.streamBlocks()
//            .buffer()
//            .catch { error(it) }
//            .collect { ok(it, eventStream.serializer) }
//    }
//
//    suspend fun consume(ok: (block: StreamBlock) -> Unit) {
//        consume(error = ::onError, ok = ok)
//    }
//}

typealias BlockSink = (StreamBlock) -> Unit

interface BlockStreamAttachable {
    val signal: Channel<Unit>

    suspend fun attach(flow: Flow<StreamBlock>, action: BlockSink) {
        runInterruptible { flow.onEach { action(it) } }.cancelOnSignal(signal)
    }

    suspend fun detach() = signal.send(Unit)
}

fun blockSink(action: BlockSink): BlockSink {
    return object : BlockStreamAttachable, BlockSink by action {
        override val signal = Channel<Unit>(1)
    }
}