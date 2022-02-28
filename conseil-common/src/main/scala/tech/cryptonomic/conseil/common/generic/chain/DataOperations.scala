package tech.cryptonomic.conseil.common.generic.chain

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Trait containing the interface for the Data
  */
trait DataOperations {

  /** Interface method for querying with given predicates
    *
    * @param  schema            name of the database schema
    * @param  tableName         name of the table which we query
    * @param  query             query predicates and fields
    * @param  hideForkInvalid should the query take care of hiding fork-invalidated entries?
    * @return query result as a map
    */
  def queryWithPredicates(schema: String, tableName: String, query: Query, hideForkInvalid: Boolean = false)(implicit
      ec: ExecutionContext
  ): Future[List[QueryResponse]]
}
