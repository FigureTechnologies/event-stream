package io.provenance.blockchain.stream.api

import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.flow.FlowCollector

interface BlockSink {
    suspend operator fun invoke(collector: FlowCollector<StreamBlock>)
}
