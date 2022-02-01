package io.provenance.eventstream.stream.models

import com.squareup.moshi.JsonClass

/**
 * Wraps a block and associated block-level and transaction-level events, as well as a marker to determine if the
 * block is "historical" (not live streamed), and metadata, if any.
 */
@JsonClass(generateAdapter = true)
data class BaseStreamBlock(
    override val block: Block,
    override val blockEvents: List<BlockEvent>,
    override val txEvents: List<TxEvent>,
    override val historical: Boolean = false
) : StreamBlock {
    override val height: Long? get() = block.header?.height
}
