package io.provenance.eventstream.stream.models.rpc.response

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RpcError(val code: Int, val log: String? = null, val message: String? = null) {
    /**
     * Checks if the blockchain RPC has experienced an unrecoverable panic.
     */
    fun isPanic(): Boolean =
        (log != null && "panic" in log.lowercase()) || (message != null && "panic" in message.lowercase())

    /**
     * Returns a unified view of the text contained in the error
     */
    fun text(): String? {
        val text = listOfNotNull(log, message).joinToString("\n")
        return if (text == "") null else text
    }

    override fun toString(): String = "$code: ${text() ?: "unknown reason"}"
}
