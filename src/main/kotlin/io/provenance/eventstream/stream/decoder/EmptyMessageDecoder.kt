package io.provenance.eventstream.stream.decoder

import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.adapter.json.decoder.Adapter as JsonDecoder
import io.provenance.eventstream.stream.models.MessageType
import io.provenance.eventstream.stream.models.RpcResponse
import org.json.JSONObject

class EmptyMessageDecoder(decoderEngine: DecoderEngine) : Decoder(decoderEngine) {

    override val priority: Int = 1

    // We have to build a reified, parameterized type suitable to pass to `moshi.adapter`
    // because it's not possible to do something like `RpcResponse<NewBlockResult>::class.java`:
    // See https://stackoverflow.com/questions/46193355/moshi-generic-type-adapter
    private val adapter: JsonDecoder<RpcResponse<JSONObject>> = decoderEngine.adapter(
        decoderEngine.parameterizedType<RpcResponse<JSONObject>>(RpcResponse::class.java, JSONObject::class.java)
    )

    override fun decode(input: String): MessageType? {
        val result = adapter.fromJson(input)?.result ?: return null
        return if (result.isEmpty) MessageType.Empty else null
    }
}