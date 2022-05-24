package io.provenance.eventstream.stream.models

data class TxData(
    val txHash: String?,
    val fee: Pair<Long?, String?>?,
    val notes: String?
)
