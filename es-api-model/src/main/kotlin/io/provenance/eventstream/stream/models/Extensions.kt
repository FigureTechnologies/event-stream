package io.provenance.eventstream.stream.models.extensions

import com.google.common.io.BaseEncoding
import cosmos.base.abci.v1beta1.Abci
import cosmos.tx.signing.v1beta1.SignatureDescriptorsKt
import cosmos.tx.signing.v1beta1.Signing
import cosmos.tx.v1beta1.TxOuterClass
import ibc.lightclients.solomachine.v1.Solomachine
import io.provenance.eventstream.stream.models.*
import io.provenance.hdwallet.bech32.Bech32
import io.provenance.hdwallet.bech32.toBech32
import io.provenance.hdwallet.common.hashing.sha256
import io.provenance.marker.v1.MsgTransferRequest
import io.provenance.marker.v1.MsgWithdrawRequest
import io.provenance.metadata.v1.p8e.SignatureSet
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

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
        val tx = TxOuterClass.Tx.parseFrom(BaseEncoding.base64().decode(tx))
        if (tx.body.messagesList[0].typeUrl != "/provenance.marker.v1.MsgWithdrawRequest") {
            tx
        }
//        val cls = Class.forName(tx.body.messagesList[0].typeUrl.substring(1)).kotlin
//        val otherThingy = cls .call(parseFrom(tx.body.messagesList[0].value)).call("administrator")
        val thingy = tx.authInfo.signerInfosList[0].publicKey.value
        val lastThingyHash = this.data?.txs?.get(index)?.hash()
        val sig = tx.authInfo.signerInfosList[0].publicKey.value.toByteArray().toHexString().toBech32()//.toBech32("tp1")
        val sig1 = tx.authInfo.signerInfosList[0].publicKey.value.toByteArray().toBech32("tp").checksum
        val sig2 = sha256(tx.signaturesList[0].toByteArray()).toBech32("tp")
        val sig3 = tx.authInfo.signerInfosList[0].publicKey.value.toByteArray().toBech32("tp").data
        val feeInfo = tx.authInfo.fee.getAmount(0)
        thingy
        val amount = feeInfo.amount.toLong()
        val denom = feeInfo.denom
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

////PubKeySecp256k1
//fun ByteArray.pubKeyToBech32(hrpPrefix: String) = let {
//    require(this.size == 33) { "Invalid Base 64 pub key byte length must be 33 not ${this.size}" }
//    require(this[0] == 0x02.toByte() || this[0] == 0x03.toByte()) { "Invalid first byte must be 2 or 3 not  ${this[0]}" }
//    val shah256 = this.toSha256()
//    val ripemd = shah256.toRIPEMD160()
//    require(ripemd.size == 20) { "RipeMD size must be 20 not ${ripemd.size}" }
//    Bech32.encode(hrpPrefix, Bech32.convertBits(ripemd, 8, 5, true))
//}
//
////PubKeySecp256k1
//fun String.pubKeyToBech32(hrpPrefix: String) = let {
//    val base64 = this.fromBase64()
//    require(base64.size == 33) { "Invalid Base 64 pub key byte length must be 33 not ${base64.size}" }
//    require(base64[0] == 0x02.toByte() || base64[0] == 0x03.toByte()) { "Invalid first byte must be 2 or 3 not  ${base64[0]}" }
//    val shah256 = base64.toSha256()
//    val ripemd = shah256.toRIPEMD160()
//    require(ripemd.size == 20) { "RipeMD size must be 20 not ${ripemd.size}" }
//    Bech32.encode(hrpPrefix, Bech32.convertBits(ripemd, 8, 5, true))
//}
//
//fun String.fromBase64() = Base64.getDecoder().decode(this)
//
//fun ByteArray.toSha256() = Hash.sha256(this)
//
//fun ByteArray.toRIPEMD160() = RIPEMD160Digest().let {
//    it.update(this, 0, this.size)
//    val buffer = ByteArray(it.getDigestSize())
//    it.doFinal(buffer, 0)
//    buffer
//}