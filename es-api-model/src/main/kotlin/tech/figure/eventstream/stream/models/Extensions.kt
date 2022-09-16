package tech.figure.eventstream.stream.models

import com.google.common.io.BaseEncoding
import cosmos.tx.v1beta1.TxOuterClass
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

fun Block.txData(index: Int): TxData? {
    val tx = this.data?.txs?.get(index) ?: return null

    val decodedTxData = TxOuterClass.Tx.parseFrom(BaseEncoding.base64().decode(tx))
    val feeData = decodedTxData.authInfo.fee

    val amount = feeData.amountList.getOrNull(0)?.amount?.toLong()
    val denom = feeData.amountList.getOrNull(0)?.denom

    val note = decodedTxData.body.memo ?: ""

    return TxData(
        this.data?.txs?.get(index)?.hash(),
        Pair(amount, denom),
        note
    )
}

fun Block.txHashes(): List<String> = this.data?.txs?.map { it.hash() } ?: emptyList()

fun Block.dateTime() = this.header?.dateTime()

fun BlockHeader.dateTime(): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(this.time, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()

fun BlockResponse.txHash(index: Int): TxData? = this.result?.block?.txData(index)

fun BlockResultsResponse.txEvents(blockDate: OffsetDateTime, txHash: (index: Int) -> TxData): List<TxEvent> =
    this.result.txEvents(blockDate, txHash)

fun BlockResultsResponseResult.txEvents(blockDateTime: OffsetDateTime?, txHash: (Int) -> TxData?): List<TxEvent> =
    run {
        txsResults?.flatMapIndexed { index: Int, tx: BlockResultsResponseResultTxsResults ->
            tx.events
                ?.filter { tx.code?.toInt() == 0 }
                ?.map { blockResultResponseEvents ->
                    blockResultResponseEvents.toTxEvent(
                        height,
                        blockDateTime,
                        txHash(index)?.txHash,
                        txHash(index)?.fee,
                        txHash(index)?.note
                    )
                } ?: emptyList()
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

fun BlockResultsResponseResult.txErroredEvents(blockDateTime: OffsetDateTime?, txHash: (Int) -> TxData?): List<TxError> =
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
        denom = fee?.second ?: ""
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
    fee: Pair<Long?, String?>?,
    note: String?
): TxEvent =
    TxEvent(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        txHash = txHash ?: "",
        eventType = this.type ?: "",
        attributes = this.attributes ?: emptyList(),
        fee = fee?.first,
        denom = fee?.second,
        note = note
    )
