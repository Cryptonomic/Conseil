package tech.cryptonomic.conseil.api.platform.data.ethereum

import cats.effect.IO
import com.typesafe.config.Config
import slick.jdbc.PostgresProfile.api._

import tech.cryptonomic.conseil.api.ApiDataOperations
import tech.cryptonomic.conseil.common.ethereum.EthereumTypes.EthereumBlockHash
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}
import tech.cryptonomic.conseil.common.util.syntax._

import scala.concurrent.ExecutionContext

/**
  * Contains list of available methods for fetching the data for Ethereum-related block-chains
  *
  * @param prefix the name of the schema under which database are stored
  */
class EthereumDataOperations(prefix: String = "ethereum", dbConfig: Config) extends ApiDataOperations {
  override lazy val dbReadHandle: Database = Database.forConfig("", dbConfig)

  /** Fetches the list of blocks for given query */
  def fetchBlocks(query: Query)(implicit ex: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates(prefix, "blocks", query).toIO

  /** Fetches the latest block, ordered by time */
  def fetchBlocksHead()(implicit ex: ExecutionContext): IO[Option[QueryResponse]] = {
    val filter = EthereumFilter(limit = Some(1), sortBy = Some("timestamp"))
    queryWithPredicates(prefix, "blocks", filter.toQuery).toIO.map(_.headOption)
  }

  /** Fetches the block by specific hash */
  def fetchBlockByHash(hash: EthereumBlockHash)(implicit ex: ExecutionContext): IO[Option[QueryResponse]] = {
    val filter = EthereumFilter(blockIds = Set(hash.value))
    queryWithPredicates(prefix, "blocks", filter.toQuery).toIO.map(_.headOption)
  }

  /** Fetches the list of transactions for given query */
  def fetchTransactions(query: Query)(implicit ex: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates(prefix, "transactions", query).toIO

  /** Fetches the transaction by specific id (different from hash) */
  def fetchTransactionByHash(hash: String)(implicit ex: ExecutionContext): IO[Option[QueryResponse]] = {
    val filter = EthereumFilter(transactionHashes = Set(hash))
    queryWithPredicates(prefix, "transactions", filter.toQuery).toIO.map(_.headOption)
  }

  /** Fetches the list of logs for given query */
  def fetchLogs(query: Query)(implicit ec: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates(prefix, "logs", query).toIO

  /** Fetches the list of reecipts for given query */
  def fetchReceipts(query: Query)(implicit ec: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates(prefix, "receipts", query).toIO

  /** Fetches the list of contracts for given query */
  def fetchContracts(query: Query)(implicit ec: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates(prefix, "contracts", query).toIO

  /** Fetches the list of tokens for given query */
  def fetchTokens(query: Query)(implicit ec: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates(prefix, "tokens", query).toIO

  /** Fetches the list of token transfers for given query */
  def fetchTokenTransfers(query: Query)(implicit ec: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates(prefix, "token_transfers", query).toIO

  /** Fetches the list of token balances for given query */
  def fetchTokensHistory(query: Query)(implicit ec: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates(prefix, "tokens_history", query).toIO

  /** Fetches the list of accounts for given query */
  def fetchAccounts(query: Query)(implicit ec: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates(prefix, "accounts", query).toIO

  /** Fetches the list of accounts for given query */
  def fetchAccountByAddress(address: String)(implicit ec: ExecutionContext): IO[Option[QueryResponse]] = {
    val filter = EthereumFilter(accountAddresses = Set(address))
    queryWithPredicates(prefix, "accounts", filter.toQuery).toIO.map(_.headOption)
  }

  /** Fetches the list of account balances for given query */
  def fetchAccountsHistory(query: Query)(implicit ec: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates(prefix, "accounts_history", query).toIO

}
