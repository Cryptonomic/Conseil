package tech.cryptonomic.conseil.common.ethereum.domain

/** Ethereum token balance */
case class TokenBalance(
    accountAddress: String,
    blockNumber: Int,
    transactionHash: String,
    tokenAddress: String,
    value: scala.math.BigDecimal,
    asof: java.sql.Timestamp
)
