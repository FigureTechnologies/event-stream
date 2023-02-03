package tech.figure.eventstream.stream.models

import cosmos.base.v1beta1.CoinOuterClass.Coin

data class TxData(
    val txHash: String?,
    val fee: Coin?,
    val note: String?
)
