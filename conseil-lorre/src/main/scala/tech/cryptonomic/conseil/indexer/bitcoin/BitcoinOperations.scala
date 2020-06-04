package tech.cryptonomic.conseil.indexer.bitcoin

// import scala.concurrent.duration._

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import doobie.util.transactor.Transactor

import tech.cryptonomic.conseil.common.rpc.RpcClient
// import tech.cryptonomic.conseil.indexer.bitcoin.rpc.json.Transaction
import tech.cryptonomic.conseil.indexer.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.indexer.bitcoin.persistence.BitcoinPersistence
import tech.cryptonomic.conseil.indexer.bitcoin.rpc.json.TransactionComponent

class BitcoinOperations[F[_]: Concurrent](
    bitcoinClient: BitcoinClient[F],
    persistence: BitcoinPersistence[F]
    // batchConf: BatchFetchConfiguration
) extends LazyLogging {

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
  def resource[F[_]: Concurrent](
      rpcClient: RpcClient[F],
      xa: Transactor[F]
  ): Resource[F, BitcoinOperations[F]] =
    for {
      bitcoinClient <- BitcoinClient.resource(rpcClient)
      persistence <- BitcoinPersistence.resource(xa)
      client <- Resource.pure(
        new BitcoinOperations[F](bitcoinClient, persistence)
      )
    } yield client
}
