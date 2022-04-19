package io.provenance.eventstream.stream.models.extensions

import com.google.common.io.BaseEncoding
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.TxInfo
import io.provenance.eventstream.stream.models.BlockHeader
import io.provenance.eventstream.stream.models.BlockResponse
import io.provenance.eventstream.stream.models.BlockResultsResponse
import io.provenance.eventstream.stream.models.BlockResultsResponseResult
import io.provenance.eventstream.stream.models.BlockResultsResponseResultEvents
import io.provenance.eventstream.stream.models.BlockResultsResponseResultTxsResults
import io.provenance.eventstream.stream.models.TxError
import io.provenance.eventstream.stream.models.TxEvent
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Compute a hex-encoded (printable) version of a SHA-256 encoded byte array.
 */
fun ByteArray.toHexString(): String = BaseEncoding.base16().encode(this)

/**
 * Compute a hex-encoded (printable) version of a SHA-256 encoded string.
 *
 * @param input An array of bytes.
 * @return An array of SHA-256 hashed bytes.
 */
fun sha256(input: ByteArray?): ByteArray =
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(input)
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException("Couldn't find a SHA-256 provider", e)
    }

/**
 * Compute a hex-encoded (printable) SHA-256 encoded string, from a base64 encoded string.
 */
fun String.hash(): String = sha256(BaseEncoding.base64().decode(this)).toHexString()

// === Date/time methods ===============================================================================================

fun Block.txData(index: Int): TxInfo? {
    val tx = this.data?.txs?.get(index)

    if (tx != null) {
        val feeInfo = TxOuterClass.Tx.parseFrom(BaseEncoding.base64().decode(tx)).authInfo.fee
        val amount = feeInfo.amountList.getOrNull(0)?.amount?.toLong()
        val denom = feeInfo.amountList.getOrNull(0)?.denom
        return TxInfo(
            this.data?.txs?.get(index)?.hash(),
            Pair(amount, denom)
        )
    }
    return null
}

fun Block.txHashes(): List<String> = this.data?.txs?.map { it.hash() } ?: emptyList()

fun Block.dateTime() = this.header?.dateTime()

fun BlockHeader.dateTime(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this.time, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()

fun BlockResponse.txHash(index: Int): TxInfo? = this.result?.block?.txData(index)

fun BlockResultsResponse.txEvents(blockDate: OffsetDateTime, txHash: (index: Int) -> TxInfo): List<TxEvent> =
    this.result.txEvents(blockDate, txHash)

fun BlockResultsResponseResult.txEvents(blockDateTime: OffsetDateTime?, txHash: (Int) -> TxInfo?): List<TxEvent> =
    run {
        txsResults?.flatMapIndexed { index: Int, tx: BlockResultsResponseResultTxsResults ->
            tx.events
                ?.map { it.toTxEvent(height, blockDateTime, txHash(index)?.txHash, txHash(index)?.fee) }
                ?: emptyList()
        }
    } ?: emptyList()

fun BlockResultsResponseResult.blockEvents(blockDateTime: OffsetDateTime?): List<BlockEvent> = run {
    beginBlockEvents?.map { e: BlockResultsResponseResultEvents ->
        BlockEvent(
            blockHeight = height,
            blockDateTime = blockDateTime,
            eventType = e.type ?: "",
            attributes = e.attributes ?: emptyList()
        )
    }
} ?: emptyList()

fun BlockResultsResponseResult.txErroredEvents(blockDateTime: OffsetDateTime?, txHash: (Int) -> TxInfo?): List<TxError> =
    run {
        txsResults?.mapIndexed { index: Int, tx: BlockResultsResponseResultTxsResults ->
            if (tx.code?.toInt() != 0) {
                tx.toBlockError(height, blockDateTime, txHash(index)?.txHash, txHash(index)?.fee)
            } else {
                null
            }
        }?.filterNotNull()
    } ?: emptyList()

fun BlockResultsResponseResultTxsResults.toBlockError(blockHeight: Long, blockDateTime: OffsetDateTime?, txHash: String?, fee: Pair<Long?, String?>?): TxError? =
    TxError(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        code = this.code?.toLong() ?: 0L,
        info = this.log ?: "",
        txHash = txHash ?: "",
        fee = fee?.first ?: 0L,
        denom = fee?.second ?: "",
        signerAddr = mutableListOf()
    )

fun BlockResultsResponseResultEvents.toBlockEvent(blockHeight: Long, blockDateTime: OffsetDateTime?): BlockEvent =
    BlockEvent(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        eventType = this.type ?: "",
        attributes = this.attributes ?: emptyList()
    )

fun BlockResultsResponseResultEvents.toTxEvent(
    blockHeight: Long,
    blockDateTime: OffsetDateTime?,
    txHash: String?,
    fee: Pair<Long?, String?>?
): TxEvent =
    TxEvent(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        txHash = txHash ?: "",
        eventType = this.type ?: "",
        attributes = this.attributes ?: emptyList(),
        fee = fee?.first,
        denom = fee?.second
    )
