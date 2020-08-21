package tech.cryptonomic.conseil.common.ethereum.rpc

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.{Pipe, Stream}
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._

import tech.cryptonomic.conseil.common.ethereum.domain.Bytecode
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.ethereum.rpc.EthereumRpcCommands._
import tech.cryptonomic.conseil.common.ethereum.rpc.json.{Block, Log, Transaction, TransactionRecipt}

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
    * @param batchSize The size of the batched request in single HTTP call
    */
  def getBlockByNumber(batchSize: Int): Pipe[F, String, Block] =
    _.map(EthGetBlockByNumber.request)
      .through(client.stream[EthGetBlockByNumber.Params, Block](batchSize))

  /**
    * Get blocks with transactions. Call JSON-RPC for each transaction from the given block.
    *
    * @param batchSize The size of the batched request in single HTTP call
    */
  def getBlockWithTransactions(batchSize: Int): Pipe[F, Block, (Block, List[Transaction], List[Log])] =
    stream =>
      stream.flatMap {
        case block if block.transactions.size > 0 =>
          Stream
            .emits(block.transactions)
            .map(EthGetTransactionByHash.request)
            .through(client.stream[EthGetTransactionByHash.Params, Transaction](batchSize))
            .chunkN(block.transactions.size)
            .map(chunks => (block, chunks.toList, Nil))
        case block => Stream.emit((block, Nil, Nil))
      }

  def getBlockWithTransactions2(batchSize: Int): Pipe[F, Block, Transaction] =
    stream =>
      stream
        .map(_.transactions)
        .flatMap(Stream.emits)
        .map(EthGetTransactionByHash.request)
        .through(client.stream[EthGetTransactionByHash.Params, Transaction](batchSize))

  def getTransactionRecipt: Pipe[F, Transaction, TransactionRecipt] =
    stream =>
      stream
        .map(_.hash)
        .map(EthGetTransactionReceipt.request)
        .through(client.stream[EthGetTransactionReceipt.Params, TransactionRecipt](batchSize = 1))

  /**
    * Get Block by number.
    *
    * @param batchSize The size of the batched request in single HTTP call
    */
  def getCode(batchSize: Int): Pipe[F, TransactionRecipt, Bytecode] =
    stream =>
      stream.collect {
        case recipt if recipt.contractAddress.isDefined =>
          EthGetCode.request(recipt.contractAddress.get, recipt.blockNumber)
      }.through(client.stream[EthGetCode.Params, Bytecode](batchSize))

  /**
    * Get transaction logs. Call JSON-RPC in batches.
    *
    * @param batchSize The size of the batched request in single HTTP call
    */
  def getLogs(batchSize: Int): Pipe[F, String, Log] =
    stream =>
      stream
        .chunkN(batchSize)
        .map { chunk =>
          for {
            head <- chunk.head
            last <- chunk.last
          } yield EthGetLogs.request(head, last, topics = Nil)
        }
        .collect {
          case Some(request) => request
        }
        .through(client.stream[EthGetLogs.Params, Seq[Log]](batchSize))
        .flatMap(Stream.emits)

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
