package io.provenance.eventstream.stream

import com.squareup.moshi.JsonClass
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockResultsResponseResultEvents

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockResult(
    val query: String?,
    val data: NewBlockEventResultData
)

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockEventResultData(
    val type: String,
    val value: NewBlockEventResultValue
)

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockEventResultBeginBlock(
    val events: List<BlockResultsResponseResultEvents>
)

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockEventResultValue(
    val block: Block,
    val result_begin_block: NewBlockEventResultBeginBlock
)
