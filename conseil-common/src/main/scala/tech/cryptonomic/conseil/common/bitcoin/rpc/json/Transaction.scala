package tech.cryptonomic.conseil.common.bitcoin.rpc.json

/**
  * Trait for transaction inputs and outputs.
  */
trait TransactionComponent {
  def txid: Option[String]
}

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
