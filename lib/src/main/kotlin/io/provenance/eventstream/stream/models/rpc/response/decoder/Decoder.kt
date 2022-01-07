package io.provenance.eventstream.stream.models.rpc.response.decoder

import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.stream.models.rpc.response.MessageType

sealed class Decoder(val engine: DecoderEngine) {
    abstract val priority: Int
    abstract fun decode(input: String): MessageType?
}
