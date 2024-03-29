package tech.cryptonomic.conseil.common.bitcoin.rpc

import cats.effect.{Async, Resource}
import fs2.{Pipe, Stream}
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._

import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.bitcoin.rpc.BitcoinRpcCommands._
import tech.cryptonomic.conseil.common.bitcoin.rpc.json.{Block, BlockchainInfo, Transaction}

/**
  * Bitcoin JSON-RPC client according to the specification at https://developer.bitcoin.org/reference/rpc/
  *
  * @param client [[RpcClient]] to use with the Bitcoin JSON-RPC api.
  *
  * * Usage example:
  *
  * {{{
  *   import cats.effect.IO
  *
  *   val bitcoinClient = new BitcoinClient[IO](rpcClient)
  *
  *   // To call [[fs2.Pipe]] methods use:
  *   Stream(1, 2).through(bitcoinClient.getBlockHash(batchSize = 10)).compile.toList
  *   // The result will be:
  *   val res0: List[String] = List(hash1, hash2)
  * }}}
  */
class BitcoinClient[F[_]: Async](client: RpcClient[F]) {

  /**
    * Get blockchain info.
    */
  def getBlockChainInfo: Stream[F, BlockchainInfo] =
    Stream(GetBlockChainInfo.request)
      .through(client.stream[Nil.type, BlockchainInfo](batchSize = 1))

  /**
    * Get Block's hash.
    *
    * @param batchSize The size of the batched request in single HTTP call
    */
  def getBlockHash(batchSize: Int): Pipe[F, Int, String] =
    _.map(GetBlockHash.request)
      .through(client.stream[GetBlockHash.Params, String](batchSize))

  /**
    * Get Block by hash.
    *
    * @param batchSize The size of the batched request in single HTTP call
    */
  def getBlockByHash(batchSize: Int): Pipe[F, String, Block] =
    _.map(GetBlock.request)
      .through(client.stream[GetBlock.Params, Block](batchSize))

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
          .emits(block.tx)
          .map(GetRawTransaction.request)
          .through(client.stream[GetRawTransaction.Params, Transaction](batchSize))
          .chunkN(block.nTx)
      } yield (block, transactions.toList)

}

object BitcoinClient {

  /**
    * Create [[cats.Resource]] with [[BitcoinClient]].
    *
    * @param client [[RpcClient]] to use with the Bitcoin JSON-RPC api.
    */
  def resource[F[_]: Async](client: RpcClient[F]): Resource[F, BitcoinClient[F]] =
    Resource.pure(new BitcoinClient[F](client))
}
