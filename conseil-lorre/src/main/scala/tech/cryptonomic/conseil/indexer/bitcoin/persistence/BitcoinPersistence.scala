package tech.cryptonomic.conseil.indexer.bitcoin.persistence

import java.sql.Timestamp

import cats.Id
import cats.effect.{Concurrent, Resource}
import cats.syntax.all._
import com.typesafe.scalalogging.LazyLogging
import fs2.{Pipe, Stream}
import slickeffect.Transactor

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.bitcoin.Tables
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._

import tech.cryptonomic.conseil.indexer.bitcoin.rpc.json.{
  Block,
  Transaction,
  TransactionComponent,
  TransactionInput,
  TransactionOutput
}
import tech.cryptonomic.conseil.indexer.bitcoin.persistence.BitcoinPersistence._
import tech.cryptonomic.conseil.common.util.Conversion

class BitcoinPersistence[F[_]: Concurrent](
    tx: Transactor[F]
) extends LazyLogging {

  def saveBlocks(batchSize: Int): Pipe[F, Block, Block] =
    _.chunkN(batchSize).evalTap { blocks =>
      Concurrent[F].delay(logger.info(s"Save Blocks in batch of: ${blocks.size}")) *>
        tx.transact(DBIO.seq(Tables.Blocks ++= blocks.toList.map(_.convertTo[Tables.BlocksRow])))
    }.flatMap(Stream.chunk)

  def saveTransactions(batchSize: Int): Pipe[F, Transaction, Transaction] =
    _.chunkN(batchSize).evalTap { transactions =>
      Concurrent[F].delay(logger.info(s"Save Transactions in batch of: ${transactions.size}")) *>
        tx.transact(
          DBIO.seq(Tables.Transactions ++= transactions.toList.map(_.convertTo[Tables.TransactionsRow]))
        )
    }.flatMap(Stream.chunk)

  def saveTransactionComponents(batchSize: Int): Pipe[F, TransactionComponent, TransactionComponent] =
    _.chunkN(batchSize).evalTap { components =>
      Concurrent[F].delay(logger.info(s"Save TransactionComponents in batch of: ${components.size}")) *>
        tx.transact(DBIO.seq(Tables.Inputs ++= components.collect {
              case vin: TransactionInput => vin.convertTo[Tables.InputsRow]
            }.toList)) *>
        tx.transact(DBIO.seq(Tables.Outputs ++= components.collect {
              case vout: TransactionOutput => vout.convertTo[Tables.OutputsRow]
            }.toList))
    }.flatMap(Stream.chunk)
}

object BitcoinPersistence {
  def resource[F[_]: Concurrent](
      tx: Transactor[F]
  ): Resource[F, BitcoinPersistence[F]] =
    for {
      client <- Resource.liftF(
        Concurrent[F].delay(new BitcoinPersistence[F](tx))
      )
    } yield client

  implicit val blockToBlocksRow: Conversion[Id, Block, Tables.BlocksRow] = new Conversion[Id, Block, Tables.BlocksRow] {
    override def convert(from: Block) =
      Tables.BlocksRow(
        hash = from.hash,
        confirmations = from.confirmations,
        size = from.size,
        weight = from.weight,
        height = from.height,
        version = from.version,
        merkleRoot = from.merkleroot,
        time = new Timestamp(from.time),
        nonce = from.nonce,
        bits = from.bits,
        difficulty = from.difficulty.toLong,
        nTx = from.tx.size,
        previousBlockHash = from.previousblockhash,
        nextBlockHash = from.nextblockhash
      )
  }

  implicit val transactionToTransactionsRow: Conversion[Id, Transaction, Tables.TransactionsRow] =
    new Conversion[Id, Transaction, Tables.TransactionsRow] {
      override def convert(from: Transaction) =
        Tables.TransactionsRow(
          txid = from.txid,
          blockhash = from.blockhash,
          hash = from.hash,
          size = from.size,
          weight = from.weight,
          version = from.version,
          confirmations = from.confirmations,
          time = new Timestamp(from.time)
        )
    }

  implicit val inputToInputsRow: Conversion[Id, TransactionInput, Tables.InputsRow] =
    new Conversion[Id, TransactionInput, Tables.InputsRow] {
      override def convert(from: TransactionInput) =
        Tables.InputsRow(
          txid = from.txid.get, // TODO: get rid of `get`
          vOut = from.vout,
          scriptSigAsm = from.scriptSig.map(_.asm),
          scriptSigHex = from.scriptSig.map(_.asm),
          sequence = from.sequence,
          coinbase = from.coinbase,
          txInWitness = from.txinwitness.map(_.toList)
        )
    }

  implicit val outputToOutputRow: Conversion[Id, TransactionOutput, Tables.OutputsRow] =
    new Conversion[Id, TransactionOutput, Tables.OutputsRow] {
      override def convert(from: TransactionOutput) =
        Tables.OutputsRow(
          txid = from.txid.get, // TODO: get rid of `get`
          value = from.value.get,
          n = from.n,
          scriptPubKeyAsm = from.scriptPubKey.asm,
          scriptPubKeyHex = from.scriptPubKey.hex,
          scriptPubKeyReqSigs = from.scriptPubKey.reqSigs,
          scriptPubKeyType = from.scriptPubKey.`type`,
          scriptPubKeyAddresses = from.scriptPubKey.addresses.map(_.toList)
        )
    }
}
