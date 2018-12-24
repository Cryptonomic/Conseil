package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import tech.cryptonomic.conseil.generic.chain.QueryProtocolPlatform
import tech.cryptonomic.conseil.generic.chain.QueryProtocolTypes.Query
import tech.cryptonomic.conseil.util.RouteHandling

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object QueryProtocol {
  def apply(implicit ec: ExecutionContext): QueryProtocol = new QueryProtocol(QueryProtocolPlatform())
}

/**
  * Platform discovery routes.
  *
  * @param queryProtocolPlatform QueryProtocolPlatform object which checks if platform exists and executes query
  * @param apiExecutionContext   is used to call the async operations exposed by the api service
  */
class QueryProtocol(queryProtocolPlatform: QueryProtocolPlatform)(implicit apiExecutionContext: ExecutionContext)
  extends LazyLogging with RouteHandling with JacksonSupport {

  val route: Route =
    get {
      pathPrefix(Segment) { platform =>
        pathPrefix(Segment) { network =>
          pathPrefix(Segment) { ent =>
            pathEnd {
              entity(as[Query]) { query: Query =>
                validateQueryOrBadRequest(query) { validatedQuery =>
                  completeWithJsonOrNotFound(queryProtocolPlatform.queryWithPredicates(platform, ent, validatedQuery))
                }
              }
            }
          }
        }
      }
    }

}
