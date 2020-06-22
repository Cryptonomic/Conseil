package tech.cryptonomic.conseil.indexer.bitcoin

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import slick.jdbc.PostgresProfile.api._
import slickeffect.Transactor

import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.bitcoin.BitcoinPersistence
import tech.cryptonomic.conseil.common.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.common.bitcoin.rpc.json.TransactionComponent
import tech.cryptonomic.conseil.indexer.config.Depth
import tech.cryptonomic.conseil.indexer.config.Newest
import tech.cryptonomic.conseil.indexer.config.Everything
import tech.cryptonomic.conseil.indexer.config.Custom
import tech.cryptonomic.conseil.common.bitcoin.Tables

/**
  * Bitcoin operations for Lorre.
  *
  * @param bitcoinClient Bitcoin client instance
  * @param persistence Bitcoin persistence instance
  * @param tx [[slickeffect.Transactor]] to perform a Slick operations on the database
  */
class BitcoinOperations[F[_]: Concurrent](
    bitcoinClient: BitcoinClient[F],
    persistence: BitcoinPersistence[F],
    tx: Transactor[F]
    // batchConf: BatchFetchConfiguration
) extends LazyLogging {

  /**
    * Start Lorre with mode defined with [[Depth]].
    *
    * @param depth Can be: Newest, Everything or Custom
    */
  def loadBlocks(depth: Depth): Stream[F, TransactionComponent] =
    Stream
      .eval(tx.transact(Tables.Blocks.sortBy(_.height).take(1).result)) // get last block height
      .zip(bitcoinClient.getBlockChainInfo)
      .flatMap { case (block, info) =>
        depth match {
          case Newest => loadBlocksWithTransactions(block.head.height to info.blocks)
          case Everything => loadBlocksWithTransactions(1 to info.blocks)
          case Custom(depth) => loadBlocksWithTransactions(depth to info.blocks)
        }
      }

  /**
    * Get Blocks from Bitcoin node through Bitcoin client and save them into the database using Slick.
    *
    * @param range Inclusive range of the block's height
    */
  def loadBlocksWithTransactions(range: Range.Inclusive): Stream[F, TransactionComponent] =
    Stream
      .range(range.start, range.end)
      .through(stream => stream)
      .through(bitcoinClient.getBlockHash(2000))
      .through(bitcoinClient.getBlockByHash(500))
      .through(persistence.saveBlocks(2000))
      .through(bitcoinClient.getTransactionsFromBlock(200))
      .through(persistence.saveTransactions(4000))
      .through(bitcoinClient.getTransactionComponents)
      .through(persistence.saveTransactionComponents(10000))
}

object BitcoinOperations {

  /**
    * Create [[cats.Resource]] with [[BitcoinOperations]].
    *
    * @param bitcoinClient JSON-RPC client instance
    * @param tx [[slickeffect.Transactor]] to perform a Slick operations on the database
    */
  def resource[F[_]: Concurrent](
      rpcClient: RpcClient[F],
      tx: Transactor[F]
  ): Resource[F, BitcoinOperations[F]] =
    for {
      bitcoinClient <- BitcoinClient.resource(rpcClient)
      persistence <- BitcoinPersistence.resource(tx)
      client <- Resource.pure(
        new BitcoinOperations[F](bitcoinClient, persistence, tx)
      )
    } yield client
}
