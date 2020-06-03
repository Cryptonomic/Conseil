package tech.cryptonomic.conseil.indexer.bitcoin.rpc.json

case class Block(
    hash: String,
    height: Int,
    tx: Seq[String]
)
