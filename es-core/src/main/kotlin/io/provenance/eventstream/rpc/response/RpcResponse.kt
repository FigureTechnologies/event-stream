package io.provenance.eventstream.stream.rpc.response

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RpcResponse<T>(
    val jsonrpc: String,
    val id: String,
    val result: T? = null,
    val error: RpcError? = null
)
