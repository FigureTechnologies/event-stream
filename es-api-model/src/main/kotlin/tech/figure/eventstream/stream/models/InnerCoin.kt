package tech.figure.eventstream.stream.models

import cosmos.base.v1beta1.CoinOuterClass.Coin
import java.math.BigInteger

data class InnerCoin(val amount: BigInteger, val denom: String) {
    constructor(coin: Coin) : this(amount = coin.amount.toBigIntegerOrNull() ?: BigInteger.ZERO, denom = coin.denom)
}
