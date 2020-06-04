package tech.cryptonomic.conseil.indexer.bitcoin.persistence

import cats.effect.{Concurrent, Resource}
import cats.syntax.all._
import com.typesafe.scalalogging.LazyLogging
import fs2.{Pipe, Stream}
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import tech.cryptonomic.conseil.indexer.bitcoin.rpc.json.{
  Block,
  Transaction,
  TransactionComponent,
  TransactionInput,
  TransactionOutput
}
import tech.cryptonomic.conseil.indexer.bitcoin.persistence.BitcoinPersistence._

class BitcoinPersistence[F[_]: Concurrent](
    xa: Transactor[F]
) extends LazyLogging {

  def saveBlocks(batchSize: Int): Pipe[F, Block, Block] =
    _.chunkN(batchSize).evalTap { blocks =>
      Concurrent[F].delay(logger.info(s"Save Blocks in batch of: ${blocks.size}")) *>
        Update[Block]("INSERT INTO bitcoin.blocks VALUES (?, ?) ON CONFLICT ON CONSTRAINT blocks_pkey DO NOTHING")
          .updateMany(blocks)
          .transact(xa)
    }.flatMap(Stream.chunk)

  def saveTransactions(batchSize: Int): Pipe[F, Transaction, Transaction] =
    _.chunkN(batchSize).evalTap { transactions =>
      Concurrent[F].delay(logger.info(s"Save Transactions in batch of: ${transactions.size}")) *>
        Update[Transaction]("INSERT INTO bitcoin.transactions VALUES (?, ?) ON CONFLICT ON CONSTRAINT transactions_pkey DO NOTHING").updateMany(transactions).transact(xa)
    }.flatMap(Stream.chunk)

  def saveTransactionComponents(batchSize: Int): Pipe[F, TransactionComponent, TransactionComponent] =
    _.chunkN(batchSize).evalTap { components =>
      Concurrent[F].delay(logger.info(s"Save TransactionComponents in batch of: ${components.size}")) *>
        Update[TransactionInput]("INSERT INTO bitcoin.inputs VALUES (?)")
          .updateMany(components.collect { case vin: TransactionInput => vin })
          .transact(xa) *>
        Update[TransactionOutput]("INSERT INTO bitcoin.outputs VALUES (?)")
          .updateMany(components.collect { case vout: TransactionOutput => vout })
          .transact(xa)
    }.flatMap(Stream.chunk)
}

object BitcoinPersistence {
  def resource[F[_]: Concurrent](
      xa: Transactor[F]
  ): Resource[F, BitcoinPersistence[F]] =
    for {
      client <- Resource.liftF(
        Concurrent[F].delay(new BitcoinPersistence[F](xa))
      )
    } yield client

  implicit val writeBlock: Write[Block] =
    Write[(String, Int)].contramap(b => (b.hash, b.height))

  implicit val writeTransaction: Write[Transaction] =
    Write[(String, String)].contramap(t => (t.txid, t.blockhash))

  implicit val writeTransactionInput: Write[TransactionInput] =
    Write[(Option[String])].contramap(c => (c.txid))
}
