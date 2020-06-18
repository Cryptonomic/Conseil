package tech.cryptonomic.conseil.common.bitcoin.rpc.json

trait TransactionComponent {
  def txid: Option[String]
}

// Response for `getrawtransaction` Bitcoin JSON-RPC api call https://developer.bitcoin.org/reference/rpc/getblock.html (with verbose = true)
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
