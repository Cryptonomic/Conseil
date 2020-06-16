package tech.cryptonomic.conseil.indexer.bitcoin.rpc.json

trait TransactionComponent {
  def txid: Option[String]
}

case class Transaction(
    txid: String,
    blockhash: String,
    hash: String,
    size: Int,
    weight: Int,
    version: Int,
    confirmations: Int,
    time: Int,
    vin: Seq[TransactionInput],
    vout: Seq[TransactionOutput]
)
