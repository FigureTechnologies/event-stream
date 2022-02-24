package io.provenance.blockchain.stream.api

import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.flow.Flow

interface BlockSource<T : StreamBlock> {
    fun streamBlocks(): Flow<T>
}
