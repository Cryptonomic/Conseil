package tech.cryptonomic.conseil.common.bitcoin.rpc.json

/**
  * Transaction output script pub key.
  */
case class ScriptPubKey(
    asm: String,
    hex: String,
    reqSigs: Option[Int],
    `type`: String,
    addresses: Option[Seq[String]]
)

/**
  * Part of the response from `getrawtransaction` Bitcoin JSON-RPC api call (with verbose = true).
  * More info at: https://developer.bitcoin.org/reference/rpc/getrawtransaction.html
  */
case class TransactionOutput(
    txid: Option[String],
    value: Option[BigDecimal],
    n: Int,
    scriptPubKey: ScriptPubKey
) extends TransactionComponent
