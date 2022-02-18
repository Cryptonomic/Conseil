package tech.cryptonomic.conseil.api.platform.data.bitcoin

import com.typesafe.config.Config
import slick.jdbc.PostgresProfile.api._

import tech.cryptonomic.conseil.api.ApiDataOperations
import tech.cryptonomic.conseil.common.bitcoin.BitcoinTypes.BitcoinBlockHash
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}
import tech.cryptonomic.conseil.api.ApiDataOperations

import scala.concurrent.ExecutionContext

import cats.effect.IO

import tech.cryptonomic.conseil.common.util.syntax._

class BitcoinDataOperations(dbConfig: Config) extends ApiDataOperations {
  override lazy val dbReadHandle: Database = Database.forConfig("", dbConfig)

  /** Fetches the list of blocks for given query */
  def fetchBlocks(query: Query)(implicit ex: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates("bitcoin", "blocks", query).toIO

  /** Fetches the latest block, ordered by time */
  def fetchBlocksHead()(implicit ex: ExecutionContext): IO[Option[QueryResponse]] = {
    val filter = BitcoinFilter(limit = Some(1), sortBy = Some("time"))
    queryWithPredicates("bitcoin", "blocks", filter.toQuery).toIO.map(_.headOption)
  }

  /** Fetches the block by specific hash */
  def fetchBlockByHash(hash: BitcoinBlockHash)(implicit ex: ExecutionContext): IO[Option[QueryResponse]] = {
    val filter = BitcoinFilter(blockIds = Set(hash.value))
    queryWithPredicates("bitcoin", "blocks", filter.toQuery).toIO.map(_.headOption)
  }

  /** Fetches the list of transactions for given query */
  def fetchTransactions(query: Query)(implicit ex: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates("bitcoin", "transactions", query).toIO

  /** Fetches the transaction by specific id (different from hash) */
  def fetchTransactionById(id: String)(implicit ex: ExecutionContext): IO[Option[QueryResponse]] = {
    val filter = BitcoinFilter(transactionIDs = Set(id))
    queryWithPredicates("bitcoin", "transactions", filter.toQuery).toIO.map(_.headOption)
  }

  /** Fetches the list of inputs for given query */
  def fetchInputs(query: Query)(implicit ex: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates("bitcoin", "inputs", query).toIO

  /** Fetches the list of outputs for given query */
  def fetchOutputs(query: Query)(implicit ex: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates("bitcoin", "outputs", query).toIO

  /** Fetches the list of accounts for given query */
  def fetchAccounts(query: Query)(implicit ex: ExecutionContext): IO[List[QueryResponse]] =
    queryWithPredicates("bitcoin", "accounts", query).toIO

  /** Fetches the account by specific address (different from hash and id) */
  def fetchAccountByAddress(address: String)(implicit ex: ExecutionContext): IO[Option[QueryResponse]] = {
    val filter = BitcoinFilter(accountAddresses = Set(address))
    queryWithPredicates("bitcoin", "accounts", filter.toQuery).toIO.map(_.headOption)
  }
}
