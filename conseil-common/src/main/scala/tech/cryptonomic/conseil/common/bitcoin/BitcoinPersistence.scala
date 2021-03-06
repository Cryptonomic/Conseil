package tech.cryptonomic.conseil.common.bitcoin

import java.sql.Timestamp
import java.time.Instant

import cats.Id
import cats.effect.{Concurrent, Resource}
import slick.jdbc.PostgresProfile.api._

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.util.Conversion
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.bitcoin.rpc.json.{Block, Transaction, TransactionInput, TransactionOutput}
import tech.cryptonomic.conseil.common.bitcoin.BitcoinPersistence._

/**
  * Bitcoin persistence into the database using Slick.
  */
class BitcoinPersistence[F[_]: Concurrent] {

  /**
    * Create [[DBIO]] seq with block (with transactions, inputs and outputs) that can be wrap into one transaction.
    *
    * @param block JSON_RPC block
    * @param transactions JSON_RPC block's transactions
    */
  def createBlock(
      block: Block,
      transactions: List[Transaction]
  ): DBIOAction[Unit, NoStream, Effect.Write] = {
    import tech.cryptonomic.conseil.common.sql.CustomProfileExtension.api._
    DBIO.seq(
      Tables.Blocks += block.convertTo[Tables.BlocksRow],
      // txid can be duplicated https://github.com/bitcoin/bitcoin/issues/612
      Tables.Transactions.insertOrUpdateAll(transactions.map(t => (t, block)).map(_.convertTo[Tables.TransactionsRow])),
      Tables.Inputs ++= transactions.flatMap(t => t.vin.map(c => (t, c, block))).map(_.convertTo[Tables.InputsRow]),
      Tables.Outputs ++= transactions.flatMap(t => t.vout.map(c => (t, c, block))).map(_.convertTo[Tables.OutputsRow])
    )
  }

  /**
    * Get sequence of existing blocks heights from the database.
    *
    * @param range Inclusive range of the block's height
    */
  def getIndexedBlockHeights(range: Range.Inclusive): DBIO[Seq[Int]] =
    Tables.Blocks.filter(_.level between (range.start, range.end)).map(_.level).result

  /**
    * Get the latest block from the database.
    */
  def getLatestIndexedBlock: DBIO[Option[Tables.BlocksRow]] =
    Tables.Blocks.sortBy(_.level.desc).take(1).result.headOption
}

object BitcoinPersistence {

  /**
    * Create [[cats.Resource]] with [[BitcoinPersistence]].
    */
  def resource[F[_]: Concurrent]: Resource[F, BitcoinPersistence[F]] =
    Resource.pure(new BitcoinPersistence[F])

  /**
    * Convert form [[Block]] to [[Tables.BlocksRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val blockToBlocksRow: Conversion[Id, Block, Tables.BlocksRow] = new Conversion[Id, Block, Tables.BlocksRow] {
    override def convert(from: Block) =
      Tables.BlocksRow(
        hash = from.hash,
        size = from.size,
        strippedSize = from.strippedsize,
        weight = from.weight,
        level = from.height,
        version = from.version,
        versionHex = from.versionHex,
        merkleRoot = from.merkleroot,
        nonce = from.nonce,
        bits = from.bits,
        difficulty = from.difficulty,
        chainWork = from.chainwork,
        nTx = from.nTx,
        previousBlockHash = from.previousblockhash,
        nextBlockHash = from.nextblockhash,
        medianTime = Timestamp.from(Instant.ofEpochSecond(from.mediantime)),
        time = Timestamp.from(Instant.ofEpochSecond(from.time))
      )
  }

  /**
    * Convert form [[Transaction]] to [[Tables.TransactionsRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val transactionToTransactionsRow: Conversion[Id, (Transaction, Block), Tables.TransactionsRow] =
    new Conversion[Id, (Transaction, Block), Tables.TransactionsRow] {
      override def convert(from: (Transaction, Block)) = from match {
        case (transaction, block) =>
          Tables.TransactionsRow(
            txid = transaction.txid,
            blockHash = block.hash,
            blockLevel = block.height,
            hash = transaction.hash,
            hex = transaction.hex,
            size = transaction.size,
            vsize = transaction.vsize,
            weight = transaction.weight,
            version = transaction.version,
            lockTime = Timestamp.from(Instant.ofEpochSecond(transaction.locktime)),
            blockTime = Timestamp.from(Instant.ofEpochSecond(transaction.blocktime))
          )
      }
    }

  /**
    * Convert form [[TransactionInput]] to [[Tables.InputsRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val inputToInputsRow: Conversion[Id, (Transaction, TransactionInput, Block), Tables.InputsRow] =
    new Conversion[Id, (Transaction, TransactionInput, Block), Tables.InputsRow] {
      override def convert(from: (Transaction, TransactionInput, Block)) = from match {
        case (transaction, input, block) =>
          Tables.InputsRow(
            txid = transaction.txid,
            blockHash = block.hash,
            blockLevel = block.height,
            blockTime = Timestamp.from(Instant.ofEpochSecond(block.time)),
            outputTxid = input.txid,
            vOut = input.vout,
            scriptSigAsm = input.scriptSig.map(_.asm),
            scriptSigHex = input.scriptSig.map(_.asm),
            sequence = input.sequence,
            coinbase = input.coinbase,
            txInWitness = input.txinwitness.map(_.mkString(","))
          )
      }
    }

  /**
    * Convert form [[TransactionOutput]] to [[OutputsRow.BlocksRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val outputToOutputRow: Conversion[Id, (Transaction, TransactionOutput, Block), Tables.OutputsRow] =
    new Conversion[Id, (Transaction, TransactionOutput, Block), Tables.OutputsRow] {
      override def convert(from: (Transaction, TransactionOutput, Block)) = from match {
        case (transaction, output, block) =>
          Tables.OutputsRow(
            txid = transaction.txid,
            blockHash = block.hash,
            blockLevel = block.height,
            blockTime = Timestamp.from(Instant.ofEpochSecond(block.time)),
            value = output.value,
            n = output.n,
            scriptPubKeyAsm = output.scriptPubKey.asm,
            scriptPubKeyHex = output.scriptPubKey.hex,
            scriptPubKeyReqSigs = output.scriptPubKey.reqSigs,
            scriptPubKeyType = output.scriptPubKey.`type`,
            scriptPubKeyAddresses = output.scriptPubKey.addresses.map(_.mkString(","))
          )
      }

    }
}
