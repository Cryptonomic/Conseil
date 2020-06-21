package tech.cryptonomic.conseil.common.bitcoin.rpc.json

/**
  * Transaction input script sig.
  */
case class ScriptSig(
    asm: String,
    hex: String
)

/**
  * Part of the response from `getrawtransaction` Bitcoin JSON-RPC api call (with verbose = true).
  * More info at: https://developer.bitcoin.org/reference/rpc/getrawtransaction.html
  */
case class TransactionInput(
    txid: Option[String],
    vout: Option[Int],
    scriptSig: Option[ScriptSig],
    sequence: Long,
    coinbase: Option[String],
    txinwitness: Option[Seq[String]]
) extends TransactionComponent
