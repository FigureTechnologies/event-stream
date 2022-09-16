package tech.figure.eventstream.stream.decoder

import tech.figure.eventstream.adapter.json.decoder.DecoderEngine
import tech.figure.eventstream.stream.rpc.response.MessageType

sealed class Decoder(val decoder: DecoderEngine) {
    abstract val priority: Int
    abstract fun decode(input: String): MessageType?
}
