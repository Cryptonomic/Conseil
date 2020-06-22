package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}

import scala.concurrent.{ExecutionContext, Future}

/** Provides pre-defined queries, which are using generic 'ApiDataOperations' for accessing the data from db */
class BitcoinDataQueries(api: ApiDataOperations) {

  def fetchBlocks(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    api
      .queryWithPredicates("bitcoin", "blocks", query)
      .map(Option(_))

  def fetchBlocksHead()(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = BitcoinFilter(limit = Some(1), sortBy = Some("time"))
    api
      .queryWithPredicates("bitcoin", "blocks", filter.toQuery)
      .map(_.headOption)
  }

  def fetchBlockByHead(hash: String)(implicit ex: ExecutionContext): Future[Option[QueryResponse]] = {
    val filter = BitcoinFilter(blockIDs = Set(hash))
    api
      .queryWithPredicates("bitcoin", "blocks", filter.toQuery)
      .map(_.headOption)
  }

  def fetchTransactions(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    api
      .queryWithPredicates("bitcoin", "transactions", query)
      .map(Some(_))

  def fetchInputs(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    api
      .queryWithPredicates("bitcoin", "inputs", query)
      .map(Some(_))

  def fetchOutputs(query: Query)(implicit ex: ExecutionContext): Future[Option[List[QueryResponse]]] =
    api
      .queryWithPredicates("bitcoin", "outputs", query)
      .map(Some(_))
}
