package io.provenance.blockchain.stream.api

import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.flow.Flow

interface BlockSource {
    suspend fun streamBlocks(): Flow<StreamBlock>
}
