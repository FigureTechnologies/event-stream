package io.provenance.eventstream.stream.decoder

import io.provenance.eventstream.adapter.json.decoder.Adapter
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.stream.NewBlockResult
import io.provenance.eventstream.stream.models.MessageType
import io.provenance.eventstream.stream.models.RpcResponse

class NewBlockDecoder(decoderEngine: DecoderEngine) : Decoder(decoderEngine) {
    override val priority: Int = 100

    // We have to build a reified, parameterized type suitable to pass to `moshi.adapter`
    // because it's not possible to do something like `RpcResponse<NewBlockResult>::class.java`:
    // See https://stackoverflow.com/questions/46193355/moshi-generic-type-adapter
    private val adapter: Adapter<RpcResponse<NewBlockResult>> = decoderEngine.adapter(
        decoderEngine.parameterizedType(RpcResponse::class.java, NewBlockResult::class.java)
    )

    override fun decode(input: String): MessageType.NewBlock? {
        return adapter.fromJson(input)?.let { it.result?.let { block -> MessageType.NewBlock(block) } }
    }
}