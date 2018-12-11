package tech.cryptonomic.conseil.generic.chain

import tech.cryptonomic.conseil.generic.chain.QueryProtocolTypes.Query
import tech.cryptonomic.conseil.tezos.ApiOperations

import scala.concurrent.{ExecutionContext, Future}

/** Companion object providing default apply implementation */
object QueryProtocolPlatform {
  def apply(): QueryProtocolPlatform = new QueryProtocolPlatform(Map("tezos" -> ApiOperations))
}

/** Class for validating if query protocol exists for the given platform
  *
  * @param operationsMap map of platformName -> QueryProtocolOperations
  * */

class QueryProtocolPlatform(operationsMap: Map[String, QueryProtocolOperations]) {

  /** Interface method for querying with given predicates
    *
    * @param  platform name of the platform which we want to query
    * @param  tableName name of the table which we query
    * @param  query     query predicates and fields
    * @return query result as a option[map]
    * */
  def queryWithPredicates(platform: String, tableName: String, query: Query)(implicit ec: ExecutionContext): Option[Future[List[Map[String, Any]]]] = {
    operationsMap.get(platform).map(_.queryWithPredicates(tableName, query))
  }
}
