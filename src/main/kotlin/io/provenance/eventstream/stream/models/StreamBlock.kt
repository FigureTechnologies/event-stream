package io.provenance.eventstream.stream.models

import com.squareup.moshi.JsonClass

/**
 * Wraps a block and associated block-level and transaction-level events, as well as a marker to determine if the
 * block is "historical" (not live-streamed), and metadata, if any.
 */
@JsonClass(generateAdapter = true)
data class StreamBlock(
    val block: Block,
    val blockEvents: List<BlockEvent>,
    val txEvents: List<TxEvent>,
    val historical: Boolean = false
) {
    val height: Long get() = block.header!!.height
}