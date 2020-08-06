package tech.cryptonomic.conseil.indexer.ethereum

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import slickeffect.Transactor

import tech.cryptonomic.conseil.common.config.Platforms.EthereumBatchFetchConfiguration
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.ethereum.EthereumPersistence
import tech.cryptonomic.conseil.common.ethereum.rpc.EthereumClient
import tech.cryptonomic.conseil.indexer.config.{Custom, Depth, Everything, Newest}

/**
  * Ethereum operations for Lorre.
  *
  * @param ethereumClient Ethereum client instance
  * @param persistence Ethereum persistence instance
  * @param tx [[slickeffect.Transactor]] to perform a Slick operations on the database
  * @param batchConf Batch processing configuration
  */
class EthereumOperations[F[_]: Concurrent](
    ethereumClient: EthereumClient[F],
    persistence: EthereumPersistence[F],
    tx: Transactor[F],
    batchConf: EthereumBatchFetchConfiguration
) extends LazyLogging {

  /**
    * Start Lorre with mode defined with [[Depth]].
    *
    * @param depth Can be: Newest, Everything or Custom
    */
  def loadBlocks(depth: Depth): Stream[F, Unit] =
    Stream
      .eval(tx.transact(persistence.getLatestIndexedBlock))
      .zip(ethereumClient.getMostRecentBlockNumber.map(Integer.decode))
      .flatMap {
        case (block, mostRecentBlockNumber) =>
          depth match {
            case Newest => loadBlocksWithTransactions(block.map(_.number + 1).getOrElse(1) to mostRecentBlockNumber)
            case Everything => loadBlocksWithTransactions(1 to mostRecentBlockNumber)
            case Custom(depth) => loadBlocksWithTransactions((mostRecentBlockNumber - depth) to mostRecentBlockNumber)
          }
      }

  /**
    * Get Blocks from Ethereum node through Ethereum client and save them into the database using Slick.
    * In the beginning, the current list of blocks is obtained from the database and removed from the computation.
    *
    * @param range Inclusive range of the block's height
    */
  def loadBlocksWithTransactions(range: Range.Inclusive): Stream[F, Unit] =
    Stream
      .eval(tx.transact(persistence.getIndexedBlockHeights(range)))
      .flatMap(
        existingBlocks =>
          Stream
            .range(range.start, range.end)
            .filter(height => !existingBlocks.contains(height))
            .map(n => s"0x${n.toHexString}")
            .through(ethereumClient.getBlockByNumber(batchConf.blocksBatchSize))
            .through(ethereumClient.getBlockWithTransactions(batchConf.transactionsBatchSize))
            .evalTap { // log every 10 block
              case (block, _) if Integer.decode(block.number) % 10 == 0 =>
                Concurrent[F].delay(logger.info(s"Save block with height: ${block.number}"))
              case _ => Concurrent[F].unit
            }
            .map((persistence.createBlock _).tupled)
            .evalMap(tx.transact)
            .drain
      )

}

object EthereumOperations {

  /**
    * Create [[cats.Resource]] with [[EthereumOperations]].
    *
    * @param rpcClient JSON-RPC client instance
    * @param tx [[slickeffect.Transactor]] to perform a Slick operations on the database
    * @param batchConf Batch processing configuration
    */
  def resource[F[_]: Concurrent](
      rpcClient: RpcClient[F],
      tx: Transactor[F],
      batchConf: EthereumBatchFetchConfiguration
  ): Resource[F, EthereumOperations[F]] =
    for {
      ethereumClient <- EthereumClient.resource(rpcClient)
      persistence <- EthereumPersistence.resource
    } yield new EthereumOperations[F](ethereumClient, persistence, tx, batchConf)
}
