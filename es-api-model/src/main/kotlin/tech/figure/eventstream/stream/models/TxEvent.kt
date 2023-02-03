package tech.figure.eventstream.stream.models

import com.squareup.moshi.JsonClass
import cosmos.base.v1beta1.CoinOuterClass.Coin
import java.time.OffsetDateTime

/**
 * Used to represent transaction-level events like "transfer", "message", metadata events
 * (`provenance.metadata.v1.EventScopeCreated`), etc.
 */
@JsonClass(generateAdapter = true)
data class TxEvent(
    val blockHeight: Long,
    val blockDateTime: OffsetDateTime?,
    val txHash: String,
    override val eventType: String,
    override val attributes: List<Event>,
    val fee: InnerCoin?,
    val note: String?
) : EncodedBlockchainEvent
