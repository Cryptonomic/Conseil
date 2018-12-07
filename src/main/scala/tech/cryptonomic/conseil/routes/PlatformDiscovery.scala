package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.JsonUtil._
import tech.cryptonomic.conseil.util.RouteHandling

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object PlatformDiscovery {
  def apply(config: Config)(implicit apiExecutionContext: ExecutionContext): PlatformDiscovery = new PlatformDiscovery(config)
}

/**
  * Platform discovery routes.
  * @param config configuration object
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class PlatformDiscovery(config: Config)(implicit apiExecutionContext: ExecutionContext) extends LazyLogging with RouteHandling {
  val route: Route =
    get {
      pathPrefix("networks") {
        pathEnd {
          complete(toJson(TezosPlatformDiscoveryOperations.getNetworks(config)))
        } ~ pathPrefix(Segment) { network =>
          pathPrefix("entities") {
            pathEnd {
              completeWithJson(TezosPlatformDiscoveryOperations.getEntities(network))
            } ~ pathPrefix(Segment) { entity =>
              pathPrefix("attributes") {
                pathEnd {
                  completeWithJson(TezosPlatformDiscoveryOperations.getTableAttributes(entity))
                } ~ pathPrefix(Segment) { attribute =>
                  pathEnd {
                    completeWithJson(TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute))
                  } ~ pathPrefix("filter") {
                    pathPrefix(Segment) { filter =>
                      pathEnd {
                        completeWithJson(TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute, Some(filter)))
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
}
