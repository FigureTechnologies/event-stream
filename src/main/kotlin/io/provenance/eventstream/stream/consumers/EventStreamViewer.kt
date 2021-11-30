package io.provenance.eventstream.stream.consumers

import io.provenance.eventstream.flow.extensions.cancelOnSignal
import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runInterruptible

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