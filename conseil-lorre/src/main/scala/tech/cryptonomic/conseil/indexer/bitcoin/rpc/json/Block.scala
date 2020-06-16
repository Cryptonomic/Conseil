package tech.cryptonomic.conseil.indexer.bitcoin.rpc.json

case class Block(
    hash: String,
    confirmations: Int,
    size: Int,
    strippedsize: Int,
    weight: Int,
    height: Int,
    version: Int,
    time: Int,
    nonce: Long,
    bits: String,
    difficulty: Double,
    merkleroot: String,
    previousblockhash: Option[String],
    nextblockhash: Option[String],
    tx: Seq[String]
)
