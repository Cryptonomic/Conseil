package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.generic.BitcoinFilter
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}

import scala.concurrent.{ExecutionContext, Future}

/** Provides pre-defined queries, which are using generic 'ApiDataOperations' for accessing the data from db */
class BitcoinDataQueries(api: ApiDataOperations) {

  /** Fetches the list of blocks for given query */
  def fetchBlocks(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    api
      .queryWithPredicates("bitcoin", "blocks", query)
      .map(Option(_))

  /** Fetches the latest block, ordered by time */
  def fetchBlocksHead()(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = BitcoinFilter(limit = Some(1), sortBy = Some("time"))
    api
      .queryWithPredicates("bitcoin", "blocks", filter.toQuery)
      .map(_.headOption)
  }

  /** Fetches the block by specific hash */
  def fetchBlockByHash(hash: String)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = BitcoinFilter(blockIds = Set(hash))
    api
      .queryWithPredicates("bitcoin", "blocks", filter.toQuery)
      .map(_.headOption)
  }

  /** Fetches the list of transactions for given query */
  def fetchTransactions(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    api
      .queryWithPredicates("bitcoin", "transactions", query)
      .map(Some(_))

  /** Fetches the transaction by specific id (different from hash) */
  def fetchTransactionById(id: String)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = BitcoinFilter(transactionIDs = Set(id))
    api
      .queryWithPredicates("bitcoin", "transactions", filter.toQuery)
      .map(_.headOption)
  }

  /** Fetches the list of inputs for given query */
  def fetchInputs(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    api
      .queryWithPredicates("bitcoin", "inputs", query)
      .map(Some(_))

  /** Fetches the list of outputs for given query */
  def fetchOutputs(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    api
      .queryWithPredicates("bitcoin", "outputs", query)
      .map(Some(_))
}
