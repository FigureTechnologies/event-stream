package io.provenance.eventstream.stream.consumers

import io.provenance.eventstream.stream.models.StreamBlock

interface BlockSink {
    suspend operator fun invoke(block: StreamBlock)
}