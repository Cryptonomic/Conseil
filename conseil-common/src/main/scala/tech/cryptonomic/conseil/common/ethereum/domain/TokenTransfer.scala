package tech.cryptonomic.conseil.common.ethereum.domain

/** Ethereum token transfer */
case class TokenTransfer(
    tokenAddress: String,
    blockNumber: Int,
    transactionHash: String,
    logIndex: String,
    fromAddress: String,
    toAddress: String,
    value: scala.math.BigDecimal
)
