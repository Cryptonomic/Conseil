package tech.cryptonomic.conseil.indexer.bitcoin.domain

case class Block(
    hash: String,
    height: Int,
    tx: Seq[String]
)
