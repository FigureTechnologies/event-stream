package io.provenance.eventstream.stream.decoder

import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.stream.rpc.response.MessageType

sealed class Decoder(val decoder: DecoderEngine) {
    abstract val priority: Int
    abstract fun decode(input: String): MessageType?
}
