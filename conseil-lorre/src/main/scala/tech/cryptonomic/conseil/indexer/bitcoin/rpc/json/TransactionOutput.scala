package tech.cryptonomic.conseil.indexer.bitcoin.rpc.json

case class TransactionOutput(
    txid: Option[String]
) extends TransactionComponent
