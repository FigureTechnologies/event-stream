package io.provenance.eventstream.stream.models

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import io.provenance.eventstream.stream.NewBlockResult
import kotlin.reflect.full.primaryConstructor
import io.provenance.eventstream.stream.decoder.Decoder as TDecoder

/**
 * A sealed class family which defines the results of decoding a Tendermint websocket/RPC API response.
 */
sealed interface MessageType {
    /**
     * Decode the supplied input into one of the variants of [MessageType].
     */
    class Decoder(val moshi: Moshi) {
        // Decoders are attempted according to their assigned priority in descending order:
        private val decoders =
            TDecoder::class.sealedSubclasses.mapNotNull { clazz -> clazz.primaryConstructor?.call(moshi) }
                .sortedByDescending { it.priority }

        fun decode(input: String): MessageType {
            for (decoder in decoders) {
                try {
                    val message = decoder.decode(input)
                    if (message != null) {
                        return message
                    }
                } catch (_: JsonDataException) {
                }
            }
            return Unknown
        }
    }

    /**
     * An unknown message was received.
     */
    object Unknown : MessageType

    /**
     * An empty message was received.
     *
     * An example of an empty message:
     *
     * ```
     * {
     *   "jsonrpc": "2.0",
     *   "id": "0",
     *   "result": {}
     * }
     * ```
     */
    object Empty : MessageType

    /**
     * An error was received from the RPC API.
     */
    data class Error(val error: RpcError) : MessageType

    /**
     * A panic message was received from the RPC API.
     */
    data class Panic(val error: RpcError) : MessageType

    /**
     * A message indicating a new block was created.
     */
    data class NewBlock(val block: NewBlockResult) : MessageType
}