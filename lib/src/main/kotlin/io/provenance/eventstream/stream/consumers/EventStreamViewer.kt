package io.provenance.eventstream.stream.consumers

import io.provenance.eventstream.Factory
import io.provenance.eventstream.stream.EventStream
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import mu.KotlinLogging

/**
 * An event stream consumer that displays blocks from the provided event stream.
 *
 * @param eventStream The event stream which provides blocks to this consumer.
 * @param options Options used to configure this consumer.
 */
@OptIn(FlowPreview::class)
@ExperimentalCoroutinesApi
class EventStreamViewer(
    private val eventStream: EventStream,
    private val options: EventStream.Options = EventStream.Options.DEFAULT
) {
    constructor(
        eventStreamFactory: Factory,
        options: EventStream.Options = EventStream.Options.DEFAULT
    ) : this(eventStreamFactory.createStream(options), options)

    private val log = KotlinLogging.logger { }

    private fun onError(error: Throwable) {
        log.error("$error")
    }

    suspend fun consume(error: (Throwable) -> Unit = ::onError, ok: (block: StreamBlock) -> Unit) {
        consume(error) { b, _ -> ok(b) }
    }

    suspend fun consume(
        error: (Throwable) -> Unit = ::onError,
        ok: (block: StreamBlock, serialize: (StreamBlockImpl) -> String) -> Unit
    ) {
        eventStream.streamBlocks()
            .buffer()
            .catch { error(it) }
            .collect { ok(it, eventStream.serializer) }
    }
}
