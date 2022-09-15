package tech.figure.eventstream.stream.models

data class TxData(
    val txHash: String?,
    val fee: Pair<Long?, String?>?,
    val note: String?
)
