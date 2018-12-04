package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives.{entity, _}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import tech.cryptonomic.conseil.tezos.QueryProtocolTypes.Query
import tech.cryptonomic.conseil.tezos.{PlatformDiscoveryOperations, QueryProtocolOperations}
import tech.cryptonomic.conseil.util.RouteHandling

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object QueryProtocol {
  def apply(implicit ec: ExecutionContext): QueryProtocol = new QueryProtocol(PlatformDiscoveryOperations)
}

/**
  * Platform discovery routes.
  *
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class QueryProtocol(platformDiscoveryOps: QueryProtocolOperations)(implicit apiExecutionContext: ExecutionContext)
  extends LazyLogging with RouteHandling with JacksonSupport {

  val route: Route =
    get {
      pathPrefix(Segment) { network =>
        pathPrefix(Segment) { ent =>
          pathEnd {
            entity(as[Query]) { fieldQuery: Query =>
              validateQueryOrBadRequest(fieldQuery) { validatedQuery =>
                completeWithJson(platformDiscoveryOps.queryWithPredicates(ent, validatedQuery))
              }
            }
          }
        }
      }
    }

}
