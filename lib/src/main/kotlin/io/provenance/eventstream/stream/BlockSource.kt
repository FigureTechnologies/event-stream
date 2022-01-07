package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.flow.Flow

interface BlockSource {
    suspend fun streamBlocks(from: Long?, toInclusive: Long? = Long.MAX_VALUE): Flow<StreamBlock>
}