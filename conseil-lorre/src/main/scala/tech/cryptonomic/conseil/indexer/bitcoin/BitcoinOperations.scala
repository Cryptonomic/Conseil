package tech.cryptonomic.conseil.indexer.bitcoin

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import slick.jdbc.PostgresProfile.api._
import slickeffect.Transactor

import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.bitcoin.{BitcoinPersistence, Tables}
import tech.cryptonomic.conseil.common.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.indexer.config.{Custom, Depth, Everything, Newest}

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
  def loadBlocks(depth: Depth): Stream[F, Unit] =
    Stream
      .eval(tx.transact(Tables.Blocks.sortBy(_.height).take(1).result)) // get last block height
      .zip(bitcoinClient.getBlockChainInfo)
      .evalTap(_ => Concurrent[F].delay(logger.info(s"Start Lorre for Bitcoin")))
      .flatMap {
        case (block, info) =>
          depth match {
            case Newest if block.size > 0 => loadBlocksWithTransactions(block.head.height to info.blocks)
            case Newest | Everything => loadBlocksWithTransactions(1 to info.blocks)
            case Custom(depth) => loadBlocksWithTransactions(depth to info.blocks)
          }
      }

  /**
    * Get Blocks from Bitcoin node through Bitcoin client and save them into the database using Slick.
    * In the beginning, the current list of blocks is obtained from the database and removed from the computation.
    *
    * @param range Inclusive range of the block's height
    */
  def loadBlocksWithTransactions(range: Range.Inclusive): Stream[F, Unit] =
    Stream
      .eval(getExistingBlocks(range))
      .flatMap(
        existingBlocks =>
          Stream
            .range(range.start, range.end)
            .filter(height => !existingBlocks.contains(height))
            .through(bitcoinClient.getBlockHash(2000))
            .through(bitcoinClient.getBlockByHash(500))
            .through(bitcoinClient.getBlockWithTransactions(200))
            .evalTap { case (block, _) => Concurrent[F].delay(logger.info(s"Save block with height: ${block.height}")) }
            .map((persistence.createBlock _).tupled)
            .evalMap(tx.transact)
            .drain
      )

  /**
    * Get sequence of existing blocks from the database.
    *
    * @param range Inclusive range of the block's height
    * @return
    */
  def getExistingBlocks(range: Range.Inclusive): F[Seq[Int]] =
    tx.transact(
      Tables.Blocks.filter(block => block.height >= range.start && block.height <= range.end).map(_.height).result
    )
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
      persistence <- BitcoinPersistence.resource
      client <- Resource.pure(
        new BitcoinOperations[F](bitcoinClient, persistence, tx)
      )
    } yield client
}
