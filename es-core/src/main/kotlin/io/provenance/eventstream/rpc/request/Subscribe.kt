package io.provenance.eventstream.rpc.request

import com.squareup.moshi.JsonClass

/**
 * Represents the "subscribe" message used to subscribe to real-time events from the Tendermint API.
 *
 * @see https://docs.tendermint.com/master/rpc/#/Websocket/subscribe
 */
@JsonClass(generateAdapter = true)
data class Subscribe(
    val jsonrpc: String = "2.0",
    val id: String = "0",
    val method: String = "subscribe",
    val params: Params
) {
    @JsonClass(generateAdapter = true)
    data class Params(val query: String)

    constructor(query: String) : this(params = Params(query))
}
