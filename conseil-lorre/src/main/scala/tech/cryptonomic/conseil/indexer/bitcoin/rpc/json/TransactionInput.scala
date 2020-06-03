package tech.cryptonomic.conseil.indexer.bitcoin.rpc.json

case class TransactionInput(
    txid: Option[String]
) extends TransactionComponent
