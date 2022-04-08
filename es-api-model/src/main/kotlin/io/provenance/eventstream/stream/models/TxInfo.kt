package io.provenance.eventstream.stream.models

data class TxInfo(
    val txHash: String? = "",
    val fee: Pair<Long?, String?>? = null
)
