package tech.cryptonomic.conseil.routes

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.routes.JsonQuery.OperationType.OperationType
import tech.cryptonomic.conseil.util.RouteHandling
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import tech.cryptonomic.conseil.routes.JsonQuery.JsonQ
import tech.cryptonomic.conseil.tezos.JsonQOperations

import scala.concurrent.ExecutionContext


object JsonQuery {

  def apply(implicit ec: ExecutionContext): JsonQuery = new JsonQuery()

  /** Enumeration of operation types */
  object OperationType extends Enumeration {
    type OperationType = Value
    val in = Value
  }

  /** Class required for KeyType enum serialization */
  class OperationTypeRef extends TypeReference[OperationType.type]
  case class Predicates(
    field: String,
    @JsonScalaEnumeration(classOf[OperationTypeRef]) operation: OperationType,
    set: List[String],
    inverse: Boolean
  )
  case class JsonQ(
    fields: List[String],
    predicates: List[Predicates]
  )

  def mapOperationToSQL(operation: OperationType): String = {
    operation match {
      case OperationType.in => "IN"
    }
  }

}

class JsonQuery(implicit ec: ExecutionContext) extends LazyLogging with RouteHandling with JacksonSupport  {

  val route: Route =
    get {
        pathEnd {
          entity(as[JsonQ]) { query: JsonQ =>
            completeWithJson(JsonQOperations.runPredicates("accounts", query))
          }
        }
    }
}
