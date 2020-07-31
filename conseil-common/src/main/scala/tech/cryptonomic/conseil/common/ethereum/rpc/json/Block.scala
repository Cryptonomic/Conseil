package tech.cryptonomic.conseil.common.ethereum.rpc.json

/**
  * Response from `eth_getBlockByHash` Ethereum JSON-RPC api call.
  * More info at: https://eth.wiki/json-rpc/API
  */
case class Block(
    number: String,
    hash: String,
    parentHash: Option[String],
    nonce: String,
    sha3Uncles: String,
    logsBloom: String,
    transactionsRoot: String,
    stateRoot: String,
    receiptsRoot: String,
    miner: String,
    mixHash: String,
    difficulty: String,
    totalDifficulty: String,
    extraData: String,
    size: String,
    gasLimit: String,
    gasUsed: String,
    timestamp: String,
    transactions: Seq[String],
    uncles: Seq[String]
)
