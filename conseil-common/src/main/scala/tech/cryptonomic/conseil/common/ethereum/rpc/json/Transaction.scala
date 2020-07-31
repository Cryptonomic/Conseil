package tech.cryptonomic.conseil.common.ethereum.rpc.json

/**
  * Response from `eth_getTransactionByHash` Ethereum JSON-RPC api call.
  * More info at: https://eth.wiki/json-rpc/API
  */
case class Transaction(
    blockHash: String,
    blockNumber: String,
    from: String,
    gas: String,
    gasPrice: String,
    hash: String,
    input: String,
    nonce: String,
    to: String,
    transactionIndex: String,
    value: String,
    v: String,
    r: String,
    s: String
)
