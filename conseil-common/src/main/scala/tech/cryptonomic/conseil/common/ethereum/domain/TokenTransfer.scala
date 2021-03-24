package tech.cryptonomic.conseil.common.ethereum.domain

/** Ethereum token transfer */
case class TokenTransfer(
    tokenAddress: String,
    blockHash: String,
    blockNumber: Int,
    timestamp: String,
    transactionHash: String,
    logIndex: String,
    fromAddress: String,
    toAddress: String,
    value: scala.math.BigDecimal
)
