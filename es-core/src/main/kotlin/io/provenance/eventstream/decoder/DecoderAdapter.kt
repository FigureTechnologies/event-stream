package io.provenance.eventstream.decoder

import io.provenance.eventstream.WsDecoderAdapter
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.adapter.json.decoder.MessageDecoder

/**
 * Create a generic [DecoderAdapter] to interface with the web socket channels.
 *
 * @param decoderEngine The [DecoderEngine] used to marshal to and from json.
 * @param wsDecoderAdapter The [WsDecoderAdapter] used to convert web socket messages into usable types.
 * @return The [DecoderAdapter] instance.
 */
fun decoderAdapter(decoderEngine: DecoderEngine, wsDecoderAdapter: WsDecoderAdapter): DecoderAdapter {
    return object : DecoderAdapter {
        override val decoderEngine: DecoderEngine = decoderEngine
        override val wsDecoder: WsDecoderAdapter = wsDecoderAdapter
    }
}

/**
 * Provide a common interface for a json framework to interface with the web socket functions.
 */
interface DecoderAdapter {
    /**
     * The [DecoderEngine] instance to wrap.
     */
    val decoderEngine: DecoderEngine

    /**
     * The [WsDecoderAdapter] instance to wrap.
     */
    val wsDecoder: WsDecoderAdapter

    /**
     * The [MessageDecoder] instance to wrap.
     *
     * Derivable from decoder engine.
     */
    val jsonDecoder: MessageDecoder get() = decoderEngine.toMessageDecoder()
}
