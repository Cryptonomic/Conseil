package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryOperations
import tech.cryptonomic.conseil.tezos.QueryProtocolTypes.FieldQuery
import tech.cryptonomic.conseil.util.RouteHandling

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object QueryProtocol {
  def apply(implicit ec: ExecutionContext): QueryProtocol = new QueryProtocol()
}

/**
  * Platform discovery routes.
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class QueryProtocol(implicit apiExecutionContext: ExecutionContext) extends LazyLogging with RouteHandling with JacksonSupport {

  val route: Route =
    get {
      entity(as[FieldQuery]) { fieldQuery: FieldQuery =>
        pathPrefix(Segment) { network =>
          pathPrefix(Segment) { entity =>
            pathEnd {
              validateQueryOrBadRequest(fieldQuery) { validatedQuery =>
                completeWithJson(PlatformDiscoveryOperations().queryWithPredicates(entity, validatedQuery))
              }
            }
          }
        }
      }
    }
}
