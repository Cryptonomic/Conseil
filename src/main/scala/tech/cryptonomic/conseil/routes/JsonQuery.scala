package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import tech.cryptonomic.conseil.routes.JsonQuery.JsonQ
import tech.cryptonomic.conseil.routes.JsonQuery.OperationType.OperationType
import tech.cryptonomic.conseil.tezos.JsonQOperations
import tech.cryptonomic.conseil.util.RouteHandling

import scala.concurrent.ExecutionContext


object JsonQuery {

  def apply(implicit ec: ExecutionContext): JsonQuery = new JsonQuery()

  def mapOperationToSQL(operation: OperationType, inverse: Boolean): String = {
    val op = operation match {
      case OperationType.in => "IN"
    }
    if (inverse) s"NOT $op"
    else op
  }

  /** Class required for KeyType enum serialization */
  class OperationTypeRef extends TypeReference[OperationType.type]

  case class Predicates(
    field: String,
    @JsonScalaEnumeration(classOf[OperationTypeRef]) operation: OperationType,
    set: List[Any],
    inverse: Boolean
  )

  case class JsonQ(
    fields: List[String],
    predicates: List[Predicates]
  )

  /** Enumeration of operation types */
  object OperationType extends Enumeration {
    type OperationType = Value
    val in = Value
  }

}

class JsonQuery(implicit ec: ExecutionContext) extends LazyLogging with RouteHandling with JacksonSupport {

  val route: Route =
    get {
      entity(as[JsonQ]) { query: JsonQ =>
        pathPrefix(Segment) { entity =>
          pathEnd {
            completeWithJson(JsonQOperations.runPredicates(entity, query))
          }
        }
      }
    }
}
