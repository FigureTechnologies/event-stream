package tech.figure.eventstream.stream

import com.squareup.moshi.JsonClass
import tech.figure.eventstream.stream.models.Block
import tech.figure.eventstream.stream.models.BlockResultsResponseResultEvents
import tech.figure.eventstream.stream.models.ConsensusParamsBlock
import tech.figure.eventstream.stream.models.ConsensusParamsEvidence
import tech.figure.eventstream.stream.models.ConsensusParamsValidator

/**
 * Response wrapper data class.
 */
@JsonClass(generateAdapter = true)
data class NewBlockResult(
    val query: String?,
    val data: NewBlockEventResultData,
    val events: Map<String, List<String>>?,
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
    val result_begin_block: NewBlockEventResultBeginBlock,
    val result_end_block: NewBlockEventResultEndBlock?,
)

@JsonClass(generateAdapter = true)
data class ConsensusParamsUpdates(
    val block: ConsensusParamsBlock?,
    val evidence: ConsensusParamsEvidence?,
    val validator: ConsensusParamsValidator?,
)

@JsonClass(generateAdapter = true)
data class NewBlockEventResultEndBlock(
    val consensus_param_updates: ConsensusParamsBlock?,
    val events: List<BlockResultsResponseResultEvents>,
)
