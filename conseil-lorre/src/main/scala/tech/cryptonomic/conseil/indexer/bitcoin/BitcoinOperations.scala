package tech.cryptonomic.conseil.indexer.bitcoin

import cats.effect.{Async, Concurrent, Resource}
import fs2.Stream
import slickeffect.Transactor

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
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
class BitcoinOperations[F[_]: Async](
    bitcoinClient: BitcoinClient[F],
    persistence: BitcoinPersistence[F],
    tx: Transactor[F],
    batchConf: BitcoinBatchFetchConfiguration
) extends ConseilLogSupport {

  /**
    * Start Lorre with mode defined with [[Depth]].
    *
    * @param depth Can be: Newest, Everything or Custom
    */
  def loadBlocks(depth: Depth, headHash: Option[String]): Stream[F, Unit] =
    Stream
      .eval(tx.transact(persistence.getLatestIndexedBlock))
      .flatMap {
        case latest if headHash.isDefined =>
          Stream(latest)
            .zip(Stream.emit(headHash.get).through(bitcoinClient.getBlockByHash(batchSize = 1)).map(_.height))
        case latest =>
          Stream(latest)
            .zip(bitcoinClient.getBlockChainInfo.map(_.blocks))

      }
      .flatMap {
        case (block, latestBlock) =>
          depth match {
            case Newest => loadBlocksWithTransactions(block.map(_.level + 1).getOrElse(1) to latestBlock)
            case Everything => loadBlocksWithTransactions(1 to latestBlock)
            case Custom(depth) => loadBlocksWithTransactions(math.max(latestBlock - depth, 1) to latestBlock)
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
      .eval(tx.transact(persistence.getIndexedBlockHeights(range)))
      .flatMap(
        existingBlocks =>
          Stream
            .range(range.start, range.end + 1)
            .filter(height => !existingBlocks.contains(height))
            .through(bitcoinClient.getBlockHash(batchConf.hashBatchSize))
            .through(bitcoinClient.getBlockByHash(batchConf.blocksBatchSize))
            .through(bitcoinClient.getBlockWithTransactions(batchConf.transactionsBatchSize))
            .evalTap { // log every 10 block
              case (block, _) if (block.height % 10 == 0) =>
                Concurrent[F].delay(logger.info(s"Save block with height: ${block.height}"))
              case _ => Concurrent[F].unit
            }
            .map((persistence.createBlock _).tupled)
            .evalTap(tx.transact)
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
  def resource[F[_]: Async](
      rpcClient: RpcClient[F],
      tx: Transactor[F],
      batchConf: BitcoinBatchFetchConfiguration
  ): Resource[F, BitcoinOperations[F]] =
    for {
      bitcoinClient <- BitcoinClient.resource(rpcClient)
      persistence <- BitcoinPersistence.resource
    } yield new BitcoinOperations[F](bitcoinClient, persistence, tx, batchConf)
}
