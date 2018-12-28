package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.generic.chain.DataPlatform
import tech.cryptonomic.conseil.generic.chain.DataTypes.Query
import tech.cryptonomic.conseil.util.RouteHandling

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object Data {
  def apply(config: PlatformsConfiguration)(implicit ec: ExecutionContext): Data = new Data(config, DataPlatform())
}

/**
  * Platform discovery routes.
  *
  * @param queryProtocolPlatform QueryProtocolPlatform object which checks if platform exists and executes query
  * @param apiExecutionContext   is used to call the async operations exposed by the api service
  */
class Data(config: PlatformsConfiguration, queryProtocolPlatform: DataPlatform)(implicit apiExecutionContext: ExecutionContext)
  extends LazyLogging with RouteHandling with JacksonSupport {

  val route: Route =
    get {
      pathPrefix(Segment) { platform =>
        pathPrefix(Segment) { network =>
          validatePlatformAndNetwork(config, platform, network) {
            pathPrefix(Segment) { ent =>
              validateEntity(ent) {
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
    }
}
