package tech.cryptonomic.conseil.common.bitcoin.rpc.json

/**
  * Response from `getblockchaininfo` Bitcoin JSON-RPC api call (with verbosity = 1).
  * All available fields at: https://developer.bitcoin.org/reference/rpc/getblockchaininfo.html
  */
case class BlockchainInfo(
    chain: String,
    blocks: Int
)
