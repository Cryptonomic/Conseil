package tech.cryptonomic.conseil.indexer.bitcoin.persistence

import cats.effect.{Concurrent, Resource}
import cats.syntax.all._
import com.typesafe.scalalogging.LazyLogging
import fs2.{Pipe, Stream}

import tech.cryptonomic.conseil.indexer.bitcoin.rpc.json.Block
import tech.cryptonomic.conseil.indexer.bitcoin.rpc.json.Transaction
import tech.cryptonomic.conseil.indexer.bitcoin.rpc.json.TransactionComponent
import tech.cryptonomic.conseil.indexer.bitcoin.rpc.json.TransactionInput
import tech.cryptonomic.conseil.indexer.bitcoin.rpc.json.TransactionOutput

class BitcoinPersistence[F[_]: Concurrent](
    // client: RpcClient[F]
    // batchConf: BatchFetchConfiguration
) extends LazyLogging {
  def saveBlocks(batchSize: Int): Pipe[F, Block, Block] =
    block =>
      block
        .chunkN(batchSize)
        .evalTap { blocks =>
          Concurrent[F].delay(logger.info(s"Save Blocks in batch of: ${blocks.size}"))
        }
        .flatMap(Stream.chunk)

  def saveTransactions(batchSize: Int): Pipe[F, Transaction, Transaction] =
    transaction =>
      transaction
        .chunkN(batchSize)
        .evalTap { transactions =>
          Concurrent[F].delay(logger.info(s"Save Transactions in batch of: ${transactions.size}"))
        }
        .flatMap(Stream.chunk)

  def saveTransactionComponents(batchSize: Int): Pipe[F, TransactionComponent, TransactionComponent] =
    component =>
      component
        .chunkN(batchSize)
        .evalTap { components =>
          Concurrent[F].delay(
            logger.info(
              s"Save TransactionsInput in batch of: ${components.collect { case vin: TransactionInput => vin }.size}"
            )
          ) *>
            Concurrent[F].delay(
              logger.info(
                s"Save TransactionsOutput in batch of: ${components.collect { case vin: TransactionOutput => vin }.size}"
              )
            )
        }
        .flatMap(Stream.chunk)
}

object BitcoinPersistence {
  def resource[F[_]: Concurrent](): Resource[F, BitcoinPersistence[F]] =
    for {
      client <- Resource.pure(
        new BitcoinPersistence[F]()
      )
    } yield client
}
