package tech.cryptonomic.conseil.common.ethereum

import java.sql.Timestamp
import java.time.Instant

import cats.Id
import cats.effect.{Concurrent, Resource}
import slick.jdbc.PostgresProfile.api._

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.util.Conversion
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.ethereum.rpc.json.{Block, Log, Transaction}
import tech.cryptonomic.conseil.common.ethereum.EthereumPersistence._
import tech.cryptonomic.conseil.common.ethereum.rpc.json.TransactionReceipt
import tech.cryptonomic.conseil.common.ethereum.domain.{Contract, Token}

/**
  * Ethereum persistence into the database using Slick.
  */
class EthereumPersistence[F[_]: Concurrent] extends ConseilLogSupport {

  /**
    * SHA-3 signature for: Transfer(address,address,uint256)
    */
  private val tokenTransferSignature = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

  /**
    * Create [[DBIO]] seq with blocks and transactions that can be wrap into one transaction.
    *
    * @param block JSON_RPC block
    * @param transactions JSON_RPC block's transactions
    */
  def createBlock(
      block: Block,
      transactions: List[Transaction],
      receipts: List[TransactionReceipt]
  ): DBIOAction[Unit, NoStream, Effect.Write] =
    DBIO.seq(
      Tables.Blocks += block.convertTo[Tables.BlocksRow],
      Tables.Transactions ++= transactions.map(_.convertTo[Tables.TransactionsRow]),
      Tables.Receipts ++= receipts.map(_.convertTo[Tables.ReceiptsRow]),
      Tables.Logs ++= receipts.flatMap(_.logs).map(_.convertTo[Tables.LogsRow]),
      Tables.TokenTransfers ++= receipts
            .flatMap(_.logs)
            .filter(
              log =>
                log.topics.size == 3 && log.topics
                    .contains(tokenTransferSignature)
            )
            .map(_.convertTo[Tables.TokenTransfersRow])
    )

  /**
    * Create [[DBIO]] seq with contracts.
    *
    * @param logs JSON_RPC contract
    */
  def createContracts(contracts: List[Contract]) =
    DBIO.seq(
      Tables.Contracts ++= contracts.map(_.convertTo[Tables.ContractsRow])
    )

  /**
    * Create [[DBIO]] seq with tokens.
    *
    * @param logs JSON_RPC token
    */
  def createTokens(tokens: List[Token]) =
    DBIO.seq(
      Tables.Tokens ++= tokens.map(_.convertTo[Tables.TokensRow])
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

  /**
    * Get a list of contract in a given block number range.
    *
    * @param range Inclusive range of the block's height
    */
  def getContracts(range: Range.Inclusive): DBIO[Seq[Tables.ContractsRow]] =
    Tables.Contracts
      .filter(_.blockNumber between (range.start, range.end))
      .filter(c => c.isErc20 || c.isErc721)
      .result
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
          value = Utils.hexStringToBigDecimal(from.value),
          v = from.v,
          r = from.r,
          s = from.s
        )
    }

  /**
    * Convert form [[TransactionReceipt]] to [[Tables.ReceiptsRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val transactionReceiptToReceiptsRow: Conversion[Id, TransactionReceipt, Tables.ReceiptsRow] =
    new Conversion[Id, TransactionReceipt, Tables.ReceiptsRow] {
      override def convert(from: TransactionReceipt) =
        Tables.ReceiptsRow(
          blockHash = from.blockHash,
          blockNumber = Integer.decode(from.blockNumber),
          transactionHash = from.transactionHash,
          transactionIndex = from.transactionIndex,
          contractAddress = from.contractAddress,
          cumulativeGasUsed = from.cumulativeGasUsed,
          gasUsed = from.gasUsed,
          logsBloom = from.logsBloom,
          status = from.status,
          root = from.root
        )
    }

  /**
    * Convert form [[Log]] to [[Tables.LogsRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val logToLogsRow: Conversion[Id, Log, Tables.LogsRow] =
    new Conversion[Id, Log, Tables.LogsRow] {
      override def convert(from: Log) =
        Tables.LogsRow(
          address = from.address,
          blockHash = from.blockHash,
          blockNumber = Integer.decode(from.blockNumber),
          data = from.data,
          logIndex = from.logIndex,
          removed = from.removed,
          topics = from.topics.mkString(","),
          transactionHash = from.transactionHash,
          transactionIndex = from.transactionIndex
        )
    }

  /**
    * Convert form [[Contract]] to [[Tables.ContractsRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val contractToContractsRow: Conversion[Id, Contract, Tables.ContractsRow] =
    new Conversion[Id, Contract, Tables.ContractsRow] {
      override def convert(from: Contract) =
        Tables.ContractsRow(
          address = from.address,
          blockHash = from.blockHash,
          blockNumber = Integer.decode(from.blockNumber),
          bytecode = from.bytecode.value,
          isErc20 = from.bytecode.isErc20,
          isErc721 = from.bytecode.isErc721
        )
    }

  /**
    * Convert form [[Log]] to [[Tables.TokenTransfersRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val logToTokenTransfersRow: Conversion[Id, Log, Tables.TokenTransfersRow] =
    new Conversion[Id, Log, Tables.TokenTransfersRow] {
      override def convert(from: Log) =
        Tables.TokenTransfersRow(
          blockNumber = Integer.decode(from.blockNumber),
          transactionHash = from.transactionHash,
          fromAddress = from.topics(1),
          toAddress = from.topics(2),
          value = Utils.hexStringToBigDecimal(from.data)
        )
    }

  /**
    * Convert form [[Token]] to [[Tables.TokensRow]]
    * TODO: This conversion should be done with the Chimney,
    *       but it's blocked due to the https://github.com/scala/bug/issues/11157
    */
  implicit val tokenToTokensRow: Conversion[Id, Token, Tables.TokensRow] =
    new Conversion[Id, Token, Tables.TokensRow] {
      override def convert(from: Token) =
        Tables.TokensRow(
          address = from.address,
          blockHash = from.blockHash,
          blockNumber = Integer.decode(from.blockNumber),
          name = from.name,
          symbol = from.symbol,
          decimals = from.decimals,
          totalSupply = from.totalSupply
        )
    }

}
