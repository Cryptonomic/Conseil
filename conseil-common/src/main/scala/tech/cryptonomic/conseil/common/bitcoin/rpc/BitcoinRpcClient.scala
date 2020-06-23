package tech.cryptonomic.conseil.common.bitcoin.rpc

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
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
  */
class BitcoinClient[F[_]: Concurrent](
    client: RpcClient[F]
) extends LazyLogging {

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
  def getBlockWithTransactions(batchSize: Int): Pipe[F, Block, (Block, List[Transaction])] = { stream =>
    for {
      block <- stream // get each Block from the stream
      transactions <- Stream
        .emits(block.tx)
        .map(GetRawTransaction.request)
        .through(client.stream[GetRawTransaction.Params, Transaction](batchSize))
        .chunkN(block.nTx)
    } yield (block, transactions.toList.map(addTxidToTransactionComponents))
  }

  /**
    * Enrich transaction components with the `txid`, because they are separated tables in the database.
    *
    * @param transaction JSON-RPC transaction response
    */
  private def addTxidToTransactionComponents(transaction: Transaction): Transaction =
    transaction.copy(
      vin = transaction.vin.map(_.copy(txid = Some(transaction.txid))),
      vout = transaction.vout.map(_.copy(txid = Some(transaction.txid)))
    )

}

object BitcoinClient {

  /**
    * Create [[cats.Resource]] with [[BitcoinClient]].
    *
    * @param client [[RpcClient]] to use with the Bitcoin JSON-RPC api.
    */
  def resource[F[_]: Concurrent](client: RpcClient[F]): Resource[F, BitcoinClient[F]] =
    for {
      client <- Resource.pure(
        new BitcoinClient[F](client)
      )
    } yield client
}
