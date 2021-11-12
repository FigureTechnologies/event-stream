package io.provenance.eventstream.extensions

import com.google.common.io.BaseEncoding
import io.provenance.eventstream.utils.sha256
import org.apache.commons.lang3.StringUtils
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// === String methods ==================================================================================================

/**
 * Remove surrounding quotation marks from a string.
 */
fun String.stripQuotes(): String = this.removeSurrounding("\"")

/**
 * Base64 decode a string. In the event of failure, the original string is returned.
 */
fun String.decodeBase64(): String =
    runCatching { BaseEncoding.base64().decode(this).decodeToString() }.getOrDefault(this)

/**
 * Checks if the string contains only ASCII printable characters.
 */
fun String.isAsciiPrintable(): Boolean = StringUtils.isAsciiPrintable(this)

/**
 * Decodes a string repeatedly base64 encoded, terminating when:
 *
 * - the decoded string stops changing or
 * - the maximum number of iterations is reached
 * - or the decoded string is no longer ASCII printable
 *
 * In the event of failure, the last successfully decoded string is returned.
 */
fun String.repeatDecodeBase64(): String {
    var s: String = this.toString() // copy
    var t: String = s.decodeBase64().stripQuotes()
    repeat(10) {
        if (s == t || !t.isAsciiPrintable()) {
            return s
        }
        s = t
        t = t.decodeBase64().stripQuotes()

    }
    return s
}

/**
 * Compute a hex-encoded (printable) SHA-256 encoded string, from a base64 encoded string.
 */
fun String.hash(): String = sha256(BaseEncoding.base64().decode(this)).toHexString()

// === Date/time methods ===============================================================================================

/**
 * Generate an ISO8601 string from the date.
 */
fun OffsetDateTime.toISOString() = this.format(DateTimeFormatter.ISO_DATE_TIME).toString()

// === ByteArray methods ===============================================================================================

/**
 * Compute a hex-encoded (printable) version of a SHA-256 encoded byte array.
 */
fun ByteArray.toHexString(): String = BaseEncoding.base16().encode(this)
