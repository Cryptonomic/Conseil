package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.routes.JsonQuery.JsonQ

import scala.concurrent.{ExecutionContext, Future}

object JsonQOperations {

  def runPredicates(table: String, query: JsonQ)(implicit ec: ExecutionContext): Future[List[Map[String, Any]]] = {
    ApiOperations.getQueryResults(TezosDatabaseOperations.selectWithPredicates(table, query.fields, query.predicates))
  }

}
