package tech.cryptonomic.conseil.common.ethereum.rpc.json

/**
  * Response from `eth_getLogs` Ethereum JSON-RPC api call.
  * More info at: https://eth.wiki/json-rpc/API
  */
case class Log(
    logIndex: String,
    transactionIndex: String,
    transactionHash: String,
    blockHash: String,
    blockNumber: String,
    address: String,
    data: String,
    topics: Seq[String],
    removed: Boolean
)
