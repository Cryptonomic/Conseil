package tech.cryptonomic.conseil.common.bitcoin.rpc.json

/**
  * Trait for transaction inputs and outputs.
  */
trait TransactionComponent

/**
  * Response from `getrawtransaction` Bitcoin JSON-RPC api call (with verbose = true).
  * More info at: https://developer.bitcoin.org/reference/rpc/getrawtransaction.html
  */
case class Transaction(
    txid: String,
    blockhash: String,
    hash: String,
    hex: String,
    size: Int,
    vsize: Int,
    weight: Int,
    version: Int,
    time: Int,
    locktime: Int,
    blocktime: Int,
    vin: Seq[TransactionInput],
    vout: Seq[TransactionOutput]
)

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
    vout: Option[Int],
    scriptSig: Option[ScriptSig],
    sequence: Long,
    coinbase: Option[String],
    txinwitness: Option[Seq[String]]
) extends TransactionComponent

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
    value: Option[BigDecimal],
    n: Int,
    scriptPubKey: ScriptPubKey
) extends TransactionComponent
