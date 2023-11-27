package tech.figure.eventstream.stream.models

import com.squareup.moshi.JsonClass

interface StreamBlock {
    val block: Block
    val blockEvents: List<BlockEvent>
    val blockResult: List<BlockResultsResponseResultTxsResultsInner>?
    val txEvents: List<TxEvent>
    val txErrors: List<TxError>
    val historical: Boolean
    val height: Long? get() = block.header?.height

    fun isEmpty(): Boolean = block.data?.txs?.isEmpty() ?: true
}

/**
 * Wraps a block and associated block-level and transaction-level events, as well as a marker to determine if the
 * block is "historical" (not live streamed), and metadata, if any.
 */
@JsonClass(generateAdapter = true)
data class StreamBlockImpl(
    override val block: Block,
    override val blockEvents: List<BlockEvent>,
    override val blockResult: List<BlockResultsResponseResultTxsResultsInner>?,
    override val txEvents: List<TxEvent>,
    override val txErrors: List<TxError>,
    override val historical: Boolean = false,
) : StreamBlock
