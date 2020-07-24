package tech.cryptonomic.conseil.common.ethereum

import java.sql.Timestamp
import java.time.Instant

import cats.Id
import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._

import tech.cryptonomic.conseil.common.util.Conversion
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.ethereum.rpc.json.{Block, Transaction}
import tech.cryptonomic.conseil.common.ethereum.EthereumPersistence._

/**
  * Ethereum persistence into the database using Slick.
  */
class EthereumPersistence[F[_]: Concurrent] extends LazyLogging {

  /**
    * Create [[DBIO]] seq with blocks and transactions that can be wrap into one transaction.
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
      Tables.Transactions ++= transactions.map(_.convertTo[Tables.TransactionsRow])
    )

  /**
    * Get sequence of existing blocks heights from the database.
    *
    * @param range Inclusive range of the block's height
    */
  def getIndexedBlockHeights(range: Range.Inclusive): DBIO[Seq[Int]] =
    Tables.Blocks.filter(_.number between (range.start, range.end)).map(_.number).result

  /**
    * Get the latest block from the database.
    */
  def getLatestIndexedBlock: DBIO[Option[Tables.BlocksRow]] =
    Tables.Blocks.sortBy(_.number.desc).take(1).result.headOption
}

object EthereumPersistence {

  /**
    * Create [[cats.Resource]] with [[EthereumPersistence]].
    */
  def resource[F[_]: Concurrent]: Resource[F, EthereumPersistence[F]] =
    Resource.pure(new EthereumPersistence[F])

  /**
    * Convert form [[Block]] to [[Tables.BlocksRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val blockToBlocksRow: Conversion[Id, Block, Tables.BlocksRow] = new Conversion[Id, Block, Tables.BlocksRow] {
    override def convert(from: Block) =
      Tables.BlocksRow(
        hash = from.hash,
        number = Integer.decode(from.number),
        difficulty = from.difficulty,
        extraData = from.extraData,
        gasLimit = from.gasLimit,
        gasUsed = from.gasUsed,
        logsBloom = from.logsBloom,
        miner = from.miner,
        mixHash = from.mixHash,
        nonce = from.nonce,
        parentHash = from.parentHash,
        receiptsRoot = from.receiptsRoot,
        sha3Uncles = from.sha3Uncles,
        size = from.size,
        stateRoot = from.stateRoot,
        totalDifficulty = from.totalDifficulty,
        transactionsRoot = from.transactionsRoot,
        uncles = Option(from.uncles).filter(_.nonEmpty).map(_.mkString(",")),
        timestamp = Timestamp.from(Instant.ofEpochSecond(Integer.decode(from.timestamp).toLong))
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
          hash = from.hash,
          blockHash = from.blockHash,
          blockNumber = Integer.decode(from.blockNumber),
          from = from.from,
          gas = from.gas,
          gasPrice = from.gasPrice,
          input = from.input,
          nonce = from.nonce,
          to = from.to,
          transactionIndex = from.transactionIndex,
          value = from.value,
          v = from.v,
          r = from.r,
          s = from.s
        )
    }

}