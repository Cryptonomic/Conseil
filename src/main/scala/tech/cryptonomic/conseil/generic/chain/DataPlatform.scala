package tech.cryptonomic.conseil.generic.chain

import tech.cryptonomic.conseil.generic.chain.DataTypes.{Query, QueryResponse}
import tech.cryptonomic.conseil.tezos.ApiOperations

import scala.concurrent.{ExecutionContext, Future}

/** Companion object providing default apply implementation */
object DataPlatform {
  def apply(): DataPlatform = new DataPlatform(Map("tezos" -> ApiOperations))
}

/** Class for validating if query protocol exists for the given platform
  *
  * @param operationsMap map of platformName -> QueryProtocolOperations
  * */

class DataPlatform(operationsMap: Map[String, DataOperations]) {
  import cats.instances.future._
  import cats.instances.option._
  import cats.syntax.traverse._
  /** Interface method for querying with given predicates
    *
    * @param  platform name of the platform which we want to query
    * @param  tableName name of the table which we query
    * @param  query     query predicates and fields
    * @return query result as a option[map]
    * */
  def queryWithPredicates(platform: String, tableName: String, query: Query)
    (implicit ec: ExecutionContext): Future[Option[List[QueryResponse]]] = {
    operationsMap.get(platform).map(_.queryWithPredicates(tableName, query)).sequence
  }
}
