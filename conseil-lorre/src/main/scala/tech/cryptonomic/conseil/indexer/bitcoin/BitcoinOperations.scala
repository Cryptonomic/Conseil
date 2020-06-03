package tech.cryptonomic.conseil.indexer.bitcoin

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream

import tech.cryptonomic.conseil.common.rpc.RpcClient
// import tech.cryptonomic.conseil.indexer.bitcoin.rpc.json.Transaction
import tech.cryptonomic.conseil.indexer.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.indexer.bitcoin.persistence.BitcoinPersistence

class BitcoinOperations[F[_]: Concurrent](
    bitcoinClient: BitcoinClient[F],
    persistence: BitcoinPersistence[F]
    // batchConf: BatchFetchConfiguration
) extends LazyLogging {

  def blockStream(range: Range.Inclusive): Stream[F, Unit] =
    Stream
      .range(range.start, range.end)
      .through(bitcoinClient.getBlockHash(2000))
      .through(bitcoinClient.getBlockByHash(500))
      .balanceThrough(Int.MaxValue, 8)(persistence.saveBlocks(2000))
      .through(bitcoinClient.getTransactionsFromBlock(500))
      .balanceThrough(Int.MaxValue, 8)(persistence.saveTransactions(2000))
      .through(bitcoinClient.getTransactionComponents)
      .balanceThrough(Int.MaxValue, 8)(persistence.saveTransactionComponents(5000))
      .drain

}

object BitcoinOperations {
  def resource[F[_]: Concurrent](
      rpcClient: RpcClient[F]
  ): Resource[F, BitcoinOperations[F]] =
    for {
      bitcoinClient <- BitcoinClient.resource(rpcClient)
      persistence <- BitcoinPersistence.resource
      client <- Resource.pure(
        new BitcoinOperations[F](bitcoinClient, persistence)
      )
    } yield client
}
