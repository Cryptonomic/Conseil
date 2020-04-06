package tech.cryptonomic.conseil.common.generic.chain

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Trait containing the interface for the Data
  */
trait DataOperations {

  /** Interface method for querying with given predicates
    *
    * @param  tableName name of the table which we query
    * @param  query     query predicates and fields
    * @return query result as a map
    * */
  def queryWithPredicates(tableName: String, query: Query)(implicit ec: ExecutionContext): Future[List[QueryResponse]]
}
