package tech.cryptonomic.conseil.indexer.bitcoin.rpc.json

trait TransactionComponent {
  def txid: Option[String]
}

case class Transaction(
    txid: String,
    blockhash: String,
    vin: Seq[TransactionInput],
    vout: Seq[TransactionOutput]
)
