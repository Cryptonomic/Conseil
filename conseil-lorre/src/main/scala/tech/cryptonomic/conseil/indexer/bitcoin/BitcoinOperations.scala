package tech.cryptonomic.conseil.indexer.bitcoin

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import slickeffect.Transactor

import tech.cryptonomic.conseil.common.config.Platforms.BitcoinBatchFetchConfiguration
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.bitcoin.BitcoinPersistence
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
    tx: Transactor[F],
    batchConf: BitcoinBatchFetchConfiguration
) extends LazyLogging {

  /**
    * Start Lorre with mode defined with [[Depth]].
    *
    * @param depth Can be: Newest, Everything or Custom
    */
  def loadBlocks(depth: Depth): Stream[F, Unit] =
    Stream
      .eval(tx.transact(persistence.getLatestIndexedBlock))
      .zip(bitcoinClient.getBlockChainInfo)
      .evalTap(_ => Concurrent[F].delay(logger.info(s"Start Lorre for Bitcoin")))
      .flatMap {
        case (block, info) =>
          depth match {
            case Newest => loadBlocksWithTransactions(block.map(_.height).getOrElse(1) to info.blocks)
            case Everything => loadBlocksWithTransactions(1 to info.blocks)
            case Custom(depth) => loadBlocksWithTransactions((info.blocks - depth) to info.blocks)
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
      .eval(tx.transact(persistence.getExistingBlocks(range)))
      .flatMap(
        existingBlocks =>
          Stream
            .range(range.start, range.end)
            .filter(height => !existingBlocks.contains(height))
            .through(bitcoinClient.getBlockHash(batchConf.hashBatchSize))
            .through(bitcoinClient.getBlockByHash(batchConf.blocksBatchSize))
            .through(bitcoinClient.getBlockWithTransactions(batchConf.transactionsBatchSize))
            .evalTap { case (block, _) => Concurrent[F].delay(logger.debug(s"Save block with height: ${block.height}")) }
            .map((persistence.createBlock _).tupled)
            .evalMap(tx.transact)
            .drain
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
      tx: Transactor[F],
      batchConf: BitcoinBatchFetchConfiguration
  ): Resource[F, BitcoinOperations[F]] =
    for {
      bitcoinClient <- BitcoinClient.resource(rpcClient)
      persistence <- BitcoinPersistence.resource
    } yield new BitcoinOperations[F](bitcoinClient, persistence, tx, batchConf)
}
