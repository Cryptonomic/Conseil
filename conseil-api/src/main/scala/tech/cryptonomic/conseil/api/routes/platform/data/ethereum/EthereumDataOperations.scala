package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.common.ethereum.EthereumTypes.EthereumBlockHash
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}
import tech.cryptonomic.conseil.common.util.DatabaseUtil

import scala.concurrent.{ExecutionContext, Future}

class EthereumDataOperations extends ApiDataOperations {
  override lazy val dbReadHandle: Database = DatabaseUtil.conseilDb

  /** Fetches the list of blocks for given query */
  def fetchBlocks(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates("ethereum", "blocks", query).map(Option(_))

  /** Fetches the latest block, ordered by time */
  def fetchBlocksHead()(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = EthereumFilter(limit = Some(1), sortBy = Some("timestamp"))
    queryWithPredicates("ethereum", "blocks", filter.toQuery).map(_.headOption)
  }

  /** Fetches the block by specific hash */
  def fetchBlockByHash(hash: EthereumBlockHash)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = EthereumFilter(blockIds = Set(hash.value))
    queryWithPredicates("ethereum", "blocks", filter.toQuery).map(_.headOption)
  }

  /** Fetches the list of transactions for given query */
  def fetchTransactions(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates("ethereum", "transactions", query).map(Some(_))

  /** Fetches the transaction by specific id (different from hash) */
  def fetchTransactionById(id: String)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = EthereumFilter(transactionIDs = Set(id))
    queryWithPredicates("ethereum", "transactions", filter.toQuery).map(_.headOption)
  }

  /** Fetches the list of logs for given query */
  def fetchLogs(query: Query)(implicit ec: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates("ethereum", "logs", query).map(Some(_))
}
