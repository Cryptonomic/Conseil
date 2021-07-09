package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import com.typesafe.config.Config
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.common.bitcoin.BitcoinTypes.BitcoinBlockHash
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}
import tech.cryptonomic.conseil.common.util.DatabaseUtil

import scala.concurrent.{ExecutionContext, Future}

class BitcoinDataOperations(dbConfig: Config) extends ApiDataOperations {
  override lazy val dbReadHandle: Database = Database.forConfig("", dbConfig)

  /** Fetches the list of blocks for given query */
  def fetchBlocks(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates("bitcoin", "blocks", query).map(Option(_))

  /** Fetches the latest block, ordered by time */
  def fetchBlocksHead()(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = BitcoinFilter(limit = Some(1), sortBy = Some("time"))
    queryWithPredicates("bitcoin", "blocks", filter.toQuery).map(_.headOption)
  }

  /** Fetches the block by specific hash */
  def fetchBlockByHash(hash: BitcoinBlockHash)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = BitcoinFilter(blockIds = Set(hash.value))
    queryWithPredicates("bitcoin", "blocks", filter.toQuery).map(_.headOption)
  }

  /** Fetches the list of transactions for given query */
  def fetchTransactions(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates("bitcoin", "transactions", query).map(Some(_))

  /** Fetches the transaction by specific id (different from hash) */
  def fetchTransactionById(id: String)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = BitcoinFilter(transactionIDs = Set(id))
    queryWithPredicates("bitcoin", "transactions", filter.toQuery).map(_.headOption)
  }

  /** Fetches the list of inputs for given query */
  def fetchInputs(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates("bitcoin", "inputs", query).map(Some(_))

  /** Fetches the list of outputs for given query */
  def fetchOutputs(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates("bitcoin", "outputs", query).map(Some(_))

  /** Fetches the list of accounts for given query */
  def fetchAccounts(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates("bitcoin", "accounts", query).map(Some(_))

  /** Fetches the account by specific address (different from hash and id) */
  def fetchAccountByAddress(address: String)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = BitcoinFilter(accountAddresses = Set(address))
    queryWithPredicates("bitcoin", "accounts", filter.toQuery).map(_.headOption)
  }
}
