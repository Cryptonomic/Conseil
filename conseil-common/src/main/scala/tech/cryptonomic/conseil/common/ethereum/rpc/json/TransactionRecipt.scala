package tech.cryptonomic.conseil.common.ethereum.rpc.json

/**
  * Response from `eth_getTransactionReceipt` Ethereum JSON-RPC api call.
  * More info at: https://eth.wiki/json-rpc/API
  */
case class TransactionReceipt(
    blockHash: String,
    blockNumber: String,
    contractAddress: Option[String],
    cumulativeGasUsed: String,
    from: String,
    gasUsed: String,
    logs: Seq[Log],
    logsBloom: String,
    status: Option[String],
    root: Option[String],
    to: Option[String],
    transactionHash: String,
    transactionIndex: String
)
