package tech.cryptonomic.conseil.common.ethereum.rpc

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.{Pipe, Stream}
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._

import tech.cryptonomic.conseil.common.ethereum.domain.{Bytecode, Contract, Token}
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.ethereum.rpc.EthereumRpcCommands._
import tech.cryptonomic.conseil.common.ethereum.rpc.json.{Block, Transaction, TransactionRecipt}
import tech.cryptonomic.conseil.common.ethereum.Utils

/**
  * Ethereum JSON-RPC client according to the specification at https://eth.wiki/json-rpc/API
  *
  * @param client [[RpcClient]] to use with the Ethereum JSON-RPC api.
  *
  * * Usage example:
  *
  * {{{
  *   import cats.effect.IO
  *
  *   val ethereumClient = new EthereumClient[IO](rpcClient)
  *
  *   // To call [[fs2.Pipe]] methods use:
  *   Stream(1, 2).through(ethereumClient.getBlockByNumber(batchSize = 10)).compile.toList
  *   // The result will be:
  *   val res0: List[Block] = List(block1, block2)
  * }}}
  */
class EthereumClient[F[_]: Concurrent](
    client: RpcClient[F]
) extends LazyLogging {

  /**
    * Get the number of most recent block.
    */
  def getMostRecentBlockNumber: Stream[F, String] =
    Stream(EthBlockNumber.request)
      .through(client.stream[EthBlockNumber.Params.type, String](batchSize = 1))

  /**
    * Get Block by number.
    *
    * @param batchSize The size of the batched request in single HTTP call.
    */
  def getBlockByNumber(batchSize: Int): Pipe[F, String, Block] =
    _.map(EthGetBlockByNumber.request)
      .through(client.stream[EthGetBlockByNumber.Params, Block](batchSize))

  /**
    * Get block's transactions. Call JSON-RPC for each transaction from the given block.
    *
    * @param batchSize The size of the batched request in single HTTP call.
    */
  def getTransactions(batchSize: Int): Pipe[F, Block, Transaction] =
    stream =>
      stream
        .map(_.transactions)
        .flatMap(Stream.emits)
        .map(EthGetTransactionByHash.request)
        .through(client.stream[EthGetTransactionByHash.Params, Transaction](batchSize))

  /**
    * Get transaction recipt.
    */
  def getTransactionRecipt: Pipe[F, Transaction, TransactionRecipt] =
    stream =>
      stream
        .map(_.hash)
        .map(EthGetTransactionReceipt.request)
        .through(client.stream[EthGetTransactionReceipt.Params, TransactionRecipt](batchSize = 1))

  /**
    * Returns contract from a given transaction recipt.
    *
    * @param batchSize The size of the batched request in single HTTP call.
    */
  def getContract(batchSize: Int): Pipe[F, TransactionRecipt, Contract] =
    stream =>
      stream.flatMap { recipt =>
        stream.collect {
          case recipt if recipt.contractAddress.isDefined =>
            EthGetCode.request(recipt.contractAddress.get, recipt.blockNumber)
        }.through(client.stream[EthGetCode.Params, Bytecode](batchSize))
          .map(
            bytecode =>
              Contract(
                address = recipt.contractAddress.get,
                blockHash = recipt.blockHash,
                blockNumber = recipt.blockNumber,
                bytecode = bytecode
              )
          )
      }

  /**
    * Get token information from given contract.
    */
  def getTokenInfo: Pipe[F, Contract, Token] =
    stream =>
      stream.flatMap { contract =>
        stream
          .map(
            contract =>
              Seq("name", "symbol", "decimals", "totalSupply")
                .map(f => EthCall.request(contract.blockNumber, contract.address, s"0x${Utils.keccak(s"$f()")}"))
          )
          .flatMap(Stream.emits)
          .through(client.stream[EthCall.Params, String](batchSize = 1))
          .chunkN(4)
          .map(_.toList)
          .collect {
            case name :: symbol :: decimals :: totalSupply :: Nil =>
              Token(
                address = contract.address,
                blockHash = contract.blockHash,
                blockNumber = contract.blockNumber,
                name = Utils.hexToString(name),
                symbol = Utils.hexToString(symbol),
                decimals = decimals,
                totalSupply = totalSupply
              )
          }
      }

}

object EthereumClient {

  /**
    * Create [[cats.Resource]] with [[EthereumClient]].
    *
    * @param client [[RpcClient]] to use with the EthereumClient JSON-RPC api.
    */
  def resource[F[_]: Concurrent](client: RpcClient[F]): Resource[F, EthereumClient[F]] =
    Resource.pure(new EthereumClient[F](client))
}
