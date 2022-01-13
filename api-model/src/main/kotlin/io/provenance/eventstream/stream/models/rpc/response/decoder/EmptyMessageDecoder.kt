package io.provenance.eventstream.stream.models.rpc.response.decoder

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.provenance.eventstream.stream.models.rpc.response.MessageType
import io.provenance.eventstream.stream.models.rpc.response.RpcResponse
import org.json.JSONObject

class EmptyMessageDecoder(moshi: Moshi) : Decoder(moshi) {

    override val priority: Int = 1

    // We have to build a reified, parameterized type suitable to pass to `moshi.adapter`
    // because it's not possible to do something like `RpcResponse<NewBlockResult>::class.java`:
    // See https://stackoverflow.com/questions/46193355/moshi-generic-type-adapter
    private val adapter: JsonAdapter<RpcResponse<JSONObject>> = moshi.adapter(
        Types.newParameterizedType(RpcResponse::class.java, JSONObject::class.java)
    )

    override fun decode(input: String): MessageType? {
        val result = adapter.fromJson(input)?.result ?: return null
        return if (result.isEmpty) MessageType.Empty else null
    }
}
