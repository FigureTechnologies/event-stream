package tech.figure.blockchain.stream.api

import tech.figure.eventstream.stream.models.StreamBlock

interface BlockSink {
    suspend operator fun invoke(block: StreamBlock)
}
