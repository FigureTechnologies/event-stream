package tech.figure.eventstream.stream.models

import com.squareup.moshi.JsonClass
import java.time.OffsetDateTime

/**
 * Used to represent block-level events like `reward`, `commission`, etc.
 */
@JsonClass(generateAdapter = true)
data class BlockEvent(
    val blockHeight: Long,
    val blockDateTime: OffsetDateTime?,
    override val eventType: String,
    override val attributes: List<Event>
) : EncodedBlockchainEvent
