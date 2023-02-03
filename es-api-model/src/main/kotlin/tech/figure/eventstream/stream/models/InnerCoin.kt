package tech.figure.eventstream.stream.models

import cosmos.base.v1beta1.CoinOuterClass.Coin

data class InnerCoin(val amount: String, val denom: String) {
    constructor(coin: Coin) : this(amount = coin.amount, denom = coin.denom)
}
