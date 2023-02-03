package tech.figure.eventstream.stream.models

import com.squareup.moshi.JsonClass
import cosmos.base.v1beta1.CoinOuterClass.Coin
import java.time.OffsetDateTime

/**
 * Represents errored Tx events that collected a fee.
 */
@JsonClass(generateAdapter = true)
data class TxError(
    val blockHeight: Long,
    val blockDateTime: OffsetDateTime?,
    val code: Long,
    val info: String,
    val txHash: String,
    val fee: Coin?,
)
