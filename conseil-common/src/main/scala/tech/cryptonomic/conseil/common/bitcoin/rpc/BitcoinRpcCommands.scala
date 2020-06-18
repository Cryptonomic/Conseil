package tech.cryptonomic.conseil.common.bitcoin.rpc

import io.circe.{Encoder, Json}

import tech.cryptonomic.conseil.common.rpc.RpcClient.RpcRequest

object BitcoinRpcCommands {
  object GetBlockHash {
    val rpcMethod = "getblockhash"
    case class Params(height: Int)
    def request(height: Int) = RpcRequest("1.0", rpcMethod, Params(height), s"gbh_$height")
  }

  object GetBlock {
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

  object GetRawTransaction {
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

