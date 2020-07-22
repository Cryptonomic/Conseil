package tech.cryptonomic.conseil.common.ethereum.rpc

import io.circe.{Encoder, Json}
import io.circe.syntax._

import tech.cryptonomic.conseil.common.rpc.RpcClient.RpcRequest

/**
  * Ethereum JSON-RPC api methods according to the specification at https://eth.wiki/json-rpc/API
  * These are selected methods necessary for the Lorre to work.
  */
object EthereumRpcCommands {

  /**
    * Sealed trait to keep the list of the RPC methods only in this file.
    */
  sealed trait EthereumRpcMethod

  /**
    * `eth_blockNumber` Ethereum JSON-RPC api method.
    * Returns the number of most recent block.
    */
  object EthBlockNumber extends EthereumRpcMethod {
    val rpcMethod = "eth_blockNumber"
    def request = RpcRequest("2.0", rpcMethod, Nil, "bn")
  }

  /**
    * `eth_getBlockByNumber` Ethereum JSON-RPC api method.
    * If verbosity is true it returns the full transaction objects,
    * if false only the hashes of the transactions.
    */
  object EthGetBlockByNumber extends EthereumRpcMethod {
    val rpcMethod = "eth_getBlockByNumber"
    case class Params(number: String, verbosity: Boolean)
    def request(number: String) = RpcRequest("2.0", rpcMethod, Params(number, false), s"egbbn_$number")

    implicit val encodeParams: Encoder[Params] = new Encoder[Params] {
      final def apply(params: Params): Json = Json.arr(
        Json.fromString(params.number),
        Json.fromBoolean(params.verbosity)
      )
    }
  }

  /**
    * `eth_getTransactionByHash` Ethereum JSON-RPC api method.
    * Returns the information about a transaction requested by transaction hash.
    */
  object EthGetTransactionByHash extends EthereumRpcMethod {
    val rpcMethod = "eth_getTransactionByHash"
    case class Params(hash: String)
    def request(hash: String) = RpcRequest("2.0", rpcMethod, Params(hash), s"egtbh_$hash")

    implicit val encodeParams: Encoder[Params] = new Encoder[Params] {
      final def apply(params: Params): Json = Json.arr(
        Json.fromString(params.hash)
      )
    }
  }

  /**
    * `eth_getLogs` Ethereum JSON-RPC api method.
    * Returns an array of all logs matching a given filter.
    */
  object EthGetLogs extends EthereumRpcMethod {
    val rpcMethod = "eth_getLogs"
    case class Params(
        fromBlock: String,
        toBlock: String,
        topics: Seq[String]
    )
    def request(fromBlock: String, toBlock: String, topics: Seq[String]) =
      RpcRequest("2.0", rpcMethod, Params(fromBlock, toBlock, topics), s"egtbh_${fromBlock}_$toBlock")

    implicit val encodeParams: Encoder[Params] = new Encoder[Params] {
      final def apply(params: Params): Json = Json.arr(
        Json.obj(
          "fromBlock" -> Json.fromString(params.fromBlock),
          "toBlock" -> Json.fromString(params.toBlock),
          "topics" -> params.topics.asJson
        )
      )
    }
  }
}
