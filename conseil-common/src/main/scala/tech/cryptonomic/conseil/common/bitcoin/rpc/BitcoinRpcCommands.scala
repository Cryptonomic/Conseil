package tech.cryptonomic.conseil.common.bitcoin.rpc

import io.circe.{Encoder, Json}

import tech.cryptonomic.conseil.common.rpc.RpcClient.RpcRequest

/**
  * Bitcoin JSON-RPC api methods according to the specification at https://developer.bitcoin.org/reference/rpc/
  * These are selected methods necessary for the Lorre to work.
  */
object BitcoinRpcCommands {

  /**
    * Sealed trait to keep the list of the RPC methods only in this file.
    */
  sealed trait BitcoinRpcMethod

  /**
    * `getblockchaininfo` Bitcoin JSON-RPC api method.
    * Returns an object containing various state info regarding blockchain processing
    * https://developer.bitcoin.org/reference/rpc/getblockchaininfo.html
    */
  object GetBlockChainInfo extends BitcoinRpcMethod {
    val rpcMethod = "getblockchaininfo"
    def request = RpcRequest("1.0", rpcMethod, Nil, "gbci")
  }

  /**
    * `getblockhash` Bitcoin JSON-RPC api method.
    * Returns hash of block in best-block-chain at height provided.
    * https://developer.bitcoin.org/reference/rpc/getblockhash.html
    */
  object GetBlockHash extends BitcoinRpcMethod {
    val rpcMethod = "getblockhash"
    case class Params(height: Int)
    def request(height: Int) = RpcRequest("1.0", rpcMethod, Params(height), s"gbh_$height")
  }

  /**
    * `getblock` Bitcoin JSON-RPC api method.
    * If verbosity is 0, returns a string that is serialized, hex-encoded data for block ‘hash’.
    * If verbosity is 1, returns an Object with information about block ‘hash’.
    * If verbosity is 2, returns an Object with information about block ‘hash’ and information about each transaction.
    * https://developer.bitcoin.org/reference/rpc/getblock.html
    */
  object GetBlock extends BitcoinRpcMethod {
    val rpcMethod = "getblock"
    case class Params(hash: String, verbosity: Int)
    def request(hash: String) = RpcRequest("1.0", rpcMethod, Params(hash, 1), s"gb_$hash")

    implicit val encodeParams: Encoder[Params] = new Encoder[Params] {
      final def apply(params: Params): Json = Json.arr(
        Json.fromString(params.hash),
        Json.fromInt(params.verbosity)
      )
    }
  }

  /**
    * `getrawtransaction` Bitcoin JSON-RPC api method.
    * Return the raw transaction data.
    * If verbose is ‘true’, returns an Object with information about ‘txid’.
    * https://developer.bitcoin.org/reference/rpc/getrawtransaction.html
    */
  object GetRawTransaction extends BitcoinRpcMethod {
    val rpcMethod = "getrawtransaction"
    case class Params(txid: String, verbose: Boolean)
    def request(txid: String) = RpcRequest("1.0", rpcMethod, Params(txid, true), s"grt_$txid")

    implicit val encodeParams: Encoder[Params] = new Encoder[Params] {
      final def apply(params: Params): Json = Json.arr(
        Json.fromString(params.txid),
        Json.fromBoolean(params.verbose)
      )
    }
  }
}
