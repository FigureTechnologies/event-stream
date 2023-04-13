package tech.figure.eventstream.stream.models

import com.google.common.io.BaseEncoding
import cosmos.base.v1beta1.CoinOuterClass.Coin
import cosmos.tx.v1beta1.TxOuterClass
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

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

fun Block.txData(index: Int): TxData? {
    val tx = this.data?.txs?.get(index) ?: return null

    val decodedTxData = TxOuterClass.Tx.parseFrom(BaseEncoding.base64().decode(tx))
    val feeData = decodedTxData.authInfo.fee

    val note = decodedTxData.body.memo ?: ""

    return TxData(
        txHash = this.data?.txs?.get(index)?.hash(),
        fee = feeData.amountList.firstOrNull()?.toInnerCoin(),
        note = note,
    )
}

/**
 * Return the hashes of the transactions contained in this block.
 *
 * @return A list of transaction hashes.
 */
fun Block.txHashes(): List<String> = this.data?.txs?.map { it.hash() } ?: emptyList()

/**
 * Return the creation time of the block.
 *
 * @return The time the block was created.  If the date is missing or invalid, null will be returned
 */
fun Block.dateTime(): OffsetDateTime? = this.header?.dateTime()

/**
 * Return the creation time of the block header.
 *
 * @return The time the block was created.  If the date is missing or invalid, null will be returned
 */
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
                    txHash(index).let { txData ->
                        blockResultResponseEvents.toTxEvent(
                            blockHeight = height,
                            blockDateTime = blockDateTime,
                            txHash = txData?.txHash,
                            fee = txData?.fee,
                            note = txData?.note,
                        )
                    }
                } ?: emptyList()
        }
    } ?: emptyList()

fun BlockResultsResponseResult.blockEvents(blockDateTime: OffsetDateTime?): List<BlockEvent> = run {
    beginBlockEvents?.map { e: BlockResultsResponseResultEvents ->
        BlockEvent(
            blockHeight = height,
            blockDateTime = blockDateTime,
            eventType = e.type ?: "",
            attributes = e.attributes ?: emptyList(),
        )
    }
} ?: emptyList()

fun BlockResultsResponseResult.txErroredEvents(blockDateTime: OffsetDateTime?, txHash: (Int) -> TxData?): List<TxError> =
    run {
        txsResults?.mapIndexed { index: Int, tx: BlockResultsResponseResultTxsResults ->
            if (tx.code?.toInt() != 0) {
                txHash(index).let { txData ->
                    tx.toBlockError(
                        blockHeight = height,
                        blockDateTime = blockDateTime,
                        txHash = txData?.txHash,
                        fee = txData?.fee,
                    )
                }
            } else {
                null
            }
        }?.filterNotNull()
    } ?: emptyList()

fun BlockResultsResponseResultTxsResults.toBlockError(blockHeight: Long, blockDateTime: OffsetDateTime?, txHash: String?, fee: InnerCoin?): TxError =
    TxError(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        code = this.code?.toLong() ?: 0L,
        info = this.log ?: "",
        txHash = txHash ?: "",
        fee = fee?.amount ?: BigInteger.ZERO,
        denom = fee?.denom ?: "",
    )

fun BlockResultsResponseResultEvents.toBlockEvent(blockHeight: Long, blockDateTime: OffsetDateTime?): BlockEvent =
    BlockEvent(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        eventType = this.type ?: "",
        attributes = this.attributes ?: emptyList(),
    )

fun BlockResultsResponseResultEvents.toTxEvent(
    blockHeight: Long,
    blockDateTime: OffsetDateTime?,
    txHash: String?,
    fee: InnerCoin?,
    note: String?,
): TxEvent =
    TxEvent(
        blockHeight = blockHeight,
        blockDateTime = blockDateTime,
        txHash = txHash ?: "",
        eventType = this.type ?: "",
        attributes = this.attributes ?: emptyList(),
        fee = fee?.amount,
        denom = fee?.denom,
        note = note,
    )

fun Coin.toInnerCoin(): InnerCoin = InnerCoin(coin = this)

/**
 * Check if a [TxEvent] contains a specific attribute.
 *
 * @param key The name of the attribute to check for.
 * @return boolean
 */
fun TxEvent.hasAttribute(key: String): Boolean = attributes.any { it.key == key }

/**
 * Convert an [Event] to an attribute, e.g. a String key/value pair, where the key and value are base64 decoded.
 *
 * @return A base64 decoded key/value value.
 */
fun Event.toAttribute(): Pair<String, String?> {
    val decoder = Base64.getDecoder()
    return String(decoder.decode(key)) to (value?.run { String(decoder.decode(this)) })
}

/**
 * Base64 decodes the contents of [EncodedBlockchainEvent.attributes], collecting the key/value pairs into a [Map]
 *
 * @return A [Map] containing the base64 decoded key/value pairs.
 */
fun EncodedBlockchainEvent.toMap(): Map<String, String?> =
    attributes.map { it.toAttribute() }.associate { it.first to it.second }.toMap()
