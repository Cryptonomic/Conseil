package tech.cryptonomic.conseil.common.bitcoin

import java.sql.Timestamp

import cats.Id
import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._

import tech.cryptonomic.conseil.common.util.Conversion
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.bitcoin.rpc.json.{Block, Transaction, TransactionInput, TransactionOutput}
import tech.cryptonomic.conseil.common.bitcoin.BitcoinPersistence._

/**
  * Bitcoin persistence into the database using Slick.
  */
class BitcoinPersistence[F[_]: Concurrent] extends LazyLogging {

  /**
    * Create [[DBIO]] seq with block (with transactions, inputs and outputs) that can be wrap into one transaction.
    *
    * @param block JSON_RPC block
    * @param transactions JSON_RPC block's transactions
    */
  def createBlock(
      block: Block,
      transactions: List[Transaction]
  ): DBIOAction[Unit, NoStream, Effect.Write] =
    DBIO.seq(
      Tables.Blocks += block.convertTo[Tables.BlocksRow],
      Tables.Transactions ++= transactions.map(_.convertTo[Tables.TransactionsRow]),
      Tables.Inputs ++= transactions.flatMap(t => t.vin.map(c => (t, c))).map(_.convertTo[Tables.InputsRow]),
      Tables.Outputs ++= transactions.flatMap(t => t.vout.map(c => (t, c))).map(_.convertTo[Tables.OutputsRow])
    )
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
        height = from.height,
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
        medianTime = new Timestamp(from.mediantime),
        time = new Timestamp(from.time)
      )
  }

  /**
    * Convert form [[Transaction]] to [[Tables.TransactionsRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val transactionToTransactionsRow: Conversion[Id, Transaction, Tables.TransactionsRow] =
    new Conversion[Id, Transaction, Tables.TransactionsRow] {
      override def convert(from: Transaction) =
        Tables.TransactionsRow(
          txid = from.txid,
          blockhash = from.blockhash,
          hash = from.hash,
          hex = from.hex,
          size = from.size,
          vsize = from.vsize,
          weight = from.weight,
          version = from.version,
          lockTime = new Timestamp(from.locktime),
          blockTime = new Timestamp(from.blocktime),
          time = new Timestamp(from.time)
        )
    }

  /**
    * Convert form [[TransactionInput]] to [[Tables.InputsRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val inputToInputsRow: Conversion[Id, (Transaction, TransactionInput), Tables.InputsRow] =
    new Conversion[Id, (Transaction, TransactionInput), Tables.InputsRow] {
      override def convert(from: (Transaction, TransactionInput)) = from match {
        case (transaction, input) =>
          Tables.InputsRow(
            txid = transaction.txid, // TODO: get rid of `get`
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
  implicit val outputToOutputRow: Conversion[Id, (Transaction, TransactionOutput), Tables.OutputsRow] =
    new Conversion[Id, (Transaction, TransactionOutput), Tables.OutputsRow] {
      override def convert(from: (Transaction, TransactionOutput)) = from match {
        case (transaction, output) =>
          Tables.OutputsRow(
            txid = transaction.txid,
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
