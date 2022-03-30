package io.provenance.eventstream.stream.decoder

import io.provenance.eventstream.WsDecoderAdapter
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.adapter.json.decoder.MessageDecoder

interface DecoderAdapter {
    fun decoderEngine(): DecoderEngine
    fun jsonDecoder(): MessageDecoder
    fun wsDecoder(): WsDecoderAdapter
}
