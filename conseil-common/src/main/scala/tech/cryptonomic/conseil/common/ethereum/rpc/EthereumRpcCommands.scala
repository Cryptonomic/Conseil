package tech.cryptonomic.conseil.common.ethereum.rpc

import io.circe.{Decoder, Encoder, HCursor, Json}

import tech.cryptonomic.conseil.common.ethereum.domain.Bytecode
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
    case object Params
    def request = RpcRequest("2.0", rpcMethod, Params, "bn")

    implicit val encodeParams: Encoder[Params.type] = (_) => Json.arr()
  }

  /**
    * `eth_getBlockByNumber` Ethereum JSON-RPC api method.
    * If verbosity is true it returns the full transaction objects,
    * if false only the hashes of the transactions.
    * We only use verbosity=false in Lorre.
    */
  object EthGetBlockByNumber extends EthereumRpcMethod {
    val rpcMethod = "eth_getBlockByNumber"
    case class Params(number: String, verbosity: Boolean)
    def request(number: String) = RpcRequest("2.0", rpcMethod, Params(number, false), s"egbbn_$number")

    implicit val encodeParams: Encoder[Params] = (params: Params) =>
      Json.arr(
        Json.fromString(params.number),
        Json.fromBoolean(params.verbosity)
      )
  }

  /**
    * `eth_getBlockByHash` Ethereum JSON-RPC api method.
    * If verbosity is true it returns the full transaction objects,
    * if false only the hashes of the transactions.
    * We only use verbosity=false in Lorre.
    */
  object EthGetBlockByHash extends EthereumRpcMethod {
    val rpcMethod = "eth_getBlockByHash"
    case class Params(hash: String, verbosity: Boolean)
    def request(hash: String) = RpcRequest("2.0", rpcMethod, Params(hash, false), s"egbbh_$hash")

    implicit val encodeParams: Encoder[Params] = (params: Params) =>
      Json.arr(
        Json.fromString(params.hash),
        Json.fromBoolean(params.verbosity)
      )
  }

  /**
    * `eth_getTransactionByHash` Ethereum JSON-RPC api method.
    * Returns the information about a transaction requested by transaction hash.
    */
  object EthGetTransactionByHash extends EthereumRpcMethod {
    val rpcMethod = "eth_getTransactionByHash"
    case class Params(hash: String)
    def request(hash: String) = RpcRequest("2.0", rpcMethod, Params(hash), s"egtbh_$hash")

    implicit val encodeParams: Encoder[Params] = (params: Params) =>
      Json.arr(
        Json.fromString(params.hash)
      )
  }

  /**
    * `eth_getBalance` Ethereum JSON-RPC api method.
    * Returns the balance of the account of given address.
    */
  object EthGetBalance extends EthereumRpcMethod {
    val rpcMethod = "eth_getBalance"
    case class Params(address: String, blockNumber: String)
    def request(address: String, blockNumber: String) =
      RpcRequest("2.0", rpcMethod, Params(address, blockNumber), s"egb_$address")

    implicit val encodeParams: Encoder[Params] = (params: Params) =>
      Json.arr(
        Json.fromString(params.address),
        Json.fromString(params.blockNumber)
      )
  }

  /**
    * `eth_getTransactionReceipt` Ethereum JSON-RPC api method.
    * Returns the transaction receipt requested.
    */
  object EthGetTransactionReceipt extends EthereumRpcMethod {
    val rpcMethod = "eth_getTransactionReceipt"
    case class Params(txHash: String)
    def request(txHash: String) = RpcRequest("2.0", rpcMethod, Params(txHash), s"egtr_$txHash")

    implicit val encodeParams: Encoder[Params] = (params: Params) =>
      Json.arr(
        Json.fromString(params.txHash)
      )
  }

  /**
    * `eth_getCode` Ethereum JSON-RPC api method.
    * Returns code at a given address.
    */
  object EthGetCode extends EthereumRpcMethod {
    val rpcMethod = "eth_getCode"
    case class Params(address: String, blockNumber: String)
    def request(address: String, blockNumber: String) =
      RpcRequest("2.0", rpcMethod, Params(address, blockNumber), s"egc_$address")

    implicit val encodeParams: Encoder[Params] = (params: Params) =>
      Json.arr(
        Json.fromString(params.address),
        Json.fromString(params.blockNumber)
      )
  }

  /**
    * `eth_call` Ethereum JSON-RPC api method.
    * Executes a new message call immediately without creating a transaction on the block chain.
    */
  object EthCall extends EthereumRpcMethod {
    val rpcMethod = "eth_call"
    case class Params(
        blockNumber: String,
        from: String,
        data: String
    )
    def request(blockNumber: String, from: String, data: String) =
      RpcRequest("2.0", rpcMethod, Params(blockNumber, from, data), s"ec_$from")

    implicit val encodeParams: Encoder[Params] = (params: Params) =>
      Json.arr(
        Json.obj(
          "to" -> Json.fromString(params.from),
          "data" -> Json.fromString(params.data)
        ),
        Json.fromString(params.blockNumber)
      )
  }

  // Decoders for the Ethereum domain case classes.
  implicit val decodeBytecode: Decoder[Bytecode] = (c: HCursor) =>
    for {
      bytecode <- c.as[String]
    } yield Bytecode(bytecode)

}
