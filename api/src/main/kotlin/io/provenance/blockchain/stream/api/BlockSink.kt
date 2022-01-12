package io.provenance.blockchain.stream.api

import io.provenance.eventstream.stream.models.StreamBlock

interface BlockSink {
    suspend operator fun invoke(streamBlock: StreamBlock)
}
