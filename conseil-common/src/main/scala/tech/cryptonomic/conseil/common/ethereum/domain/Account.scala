package tech.cryptonomic.conseil.common.ethereum.domain

/**
  * Ethereum account containing optional contract data.
  */
case class Account(
    address: String,
    blockHash: String,
    blockNumber: String,
    timestamp: String,
    balance: scala.math.BigDecimal,
    bytecode: Option[Bytecode] = None,
    tokenStandard: Option[TokenStandard] = None,
    name: Option[String] = None,
    symbol: Option[String] = None,
    decimals: Option[Int] = None,
    totalSupply: Option[scala.math.BigDecimal] = None
)
