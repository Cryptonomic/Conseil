package tech.cryptonomic.conseil.common.bitcoin.rpc.json

// Response for `getblock` Bitcoin JSON-RPC api call https://developer.bitcoin.org/reference/rpc/getblock.html (with verbosity = 1)
case class Block(
    hash: String,
    size: Int,
    strippedsize: Int,
    weight: Int,
    height: Int,
    version: Int,
    versionHex: String,
    merkleroot: String,
    nonce: Long,
    bits: String,
    difficulty: BigDecimal,
    chainwork: String,
    nTx: Int,
    previousblockhash: Option[String],
    nextblockhash: Option[String],
    time: Int,
    mediantime: Int,
    tx: Seq[String]
)
