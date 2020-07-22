package tech.cryptonomic.conseil.common.ethereum.rpc

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.{Pipe, Stream}
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._

import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.ethereum.rpc.EthereumRpcCommands._
import tech.cryptonomic.conseil.common.ethereum.rpc.json.{Block, Transaction}

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
  *   Stream("0x1", "0x2").through(ethereumClient.getBlockByNumber(batchSize = 10)).compile.toList
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
      .through(client.stream[Nil.type, String](batchSize = 1))

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
  def getBlockWithTransactions(batchSize: Int): Pipe[F, Block, (Block, List[Transaction])] =
    stream =>
      for {
        block <- stream // get each Block from the stream
        transactions <- Stream
          .emits(block.transactions)
          .map(EthGetTransactionByHash.request)
          .through(client.stream[EthGetTransactionByHash.Params, Transaction](batchSize))
          .chunkN(block.transactions.size)
      } yield (block, transactions.toList)

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
