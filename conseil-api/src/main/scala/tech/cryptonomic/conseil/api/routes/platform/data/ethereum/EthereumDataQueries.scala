package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.common.ethereum.EthereumTypes.EthereumBlockHash
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}

import scala.concurrent.{ExecutionContext, Future}

/** Provides pre-defined queries, which are using generic 'ApiDataOperations' for accessing the data from db */
class EthereumDataQueries(api: ApiDataOperations) {

  /** Fetches the list of blocks for given query */
  def fetchBlocks(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    api
      .queryWithPredicates("ethereum", "blocks", query)
      .map(Option(_))

  /** Fetches the latest block, ordered by time */
  def fetchBlocksHead(network: String)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = EthereumFilter(limit = Some(1), sortBy = Some("time"))
    api
      .queryWithPredicates("ethereum", "blocks", filter.toQuery(network))
      .map(_.headOption)
  }

  /** Fetches the block by specific hash */
  def fetchBlockByHash(network: String, hash: EthereumBlockHash)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = EthereumFilter(blockIds = Set(hash.value))
    api
      .queryWithPredicates("ethereum", "blocks", filter.toQuery(network))
      .map(_.headOption)
  }

  /** Fetches the list of transactions for given query */
  def fetchTransactions(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    api
      .queryWithPredicates("ethereum", "transactions", query)
      .map(Some(_))

  /** Fetches the transaction by specific id (different from hash) */
  def fetchTransactionById(network: String, id: String)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = EthereumFilter(transactionIDs = Set(id))
    api
      .queryWithPredicates("ethereum", "transactions", filter.toQuery(network))
      .map(_.headOption)
  }
}
