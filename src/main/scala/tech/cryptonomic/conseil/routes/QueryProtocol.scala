package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{entity, _}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import tech.cryptonomic.conseil.generic.chain.QueryProtocolOperations
import tech.cryptonomic.conseil.generic.chain.QueryProtocolTypes.Query
import tech.cryptonomic.conseil.tezos.ApiOperations
import tech.cryptonomic.conseil.util.RouteHandling

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object QueryProtocol {
  def apply(implicit ec: ExecutionContext): QueryProtocol = new QueryProtocol(ApiOperations)
}

/**
  * Platform discovery routes.
  *
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class QueryProtocol(queryProtocolOps: QueryProtocolOperations)(implicit apiExecutionContext: ExecutionContext)
  extends LazyLogging with RouteHandling with JacksonSupport {

  private val supportedPlatforms = List("tezos")

  val route: Route =
    get {
      pathPrefix(Segment) { platform =>
          pathPrefix(Segment) { ent =>
            pathEnd {
              entity(as[Query]) { query: Query =>
                if(supportedPlatforms.contains(platform)) {
                  validateQueryOrBadRequest(query) { validatedQuery =>
                    completeWithJson(queryProtocolOps.queryWithPredicates(ent, validatedQuery))
                  }
                } else {
                  complete(StatusCodes.NotFound)
                }
              }
            }
          }
      }
    }
}
