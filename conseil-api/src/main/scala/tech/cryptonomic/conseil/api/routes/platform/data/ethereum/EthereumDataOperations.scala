package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.common.ethereum.EthereumTypes.EthereumBlockHash
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}
import tech.cryptonomic.conseil.common.util.DatabaseUtil

import scala.concurrent.{ExecutionContext, Future}

/**
  * Contains list of available methods for fetching the data for Ethereum-related block-chains
  *
  * @param prefix the name of the schema under which database are stored
  */
class EthereumDataOperations(prefix: String) extends ApiDataOperations {
  override lazy val dbReadHandle: Database = DatabaseUtil.conseilDb

  /** Fetches the list of blocks for given query */
  def fetchBlocks(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates(prefix, "blocks", query).map(Option(_))

  /** Fetches the latest block, ordered by time */
  def fetchBlocksHead()(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = EthereumFilter(limit = Some(1), sortBy = Some("timestamp"))
    queryWithPredicates(prefix, "blocks", filter.toQuery).map(_.headOption)
  }

  /** Fetches the block by specific hash */
  def fetchBlockByHash(hash: EthereumBlockHash)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = EthereumFilter(blockIds = Set(hash.value))
    queryWithPredicates(prefix, "blocks", filter.toQuery).map(_.headOption)
  }

  /** Fetches the list of transactions for given query */
  def fetchTransactions(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates(prefix, "transactions", query).map(Some(_))

  /** Fetches the transaction by specific id (different from hash) */
  def fetchTransactionByHash(hash: String)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = EthereumFilter(transactionHashes = Set(hash))
    queryWithPredicates(prefix, "transactions", filter.toQuery).map(_.headOption)
  }

  /** Fetches the list of logs for given query */
  def fetchLogs(query: Query)(implicit ec: ExecutionContext): Future[Option[List[QueryResponse]]] =
    queryWithPredicates(prefix, "logs", query).map(Some(_))
}
