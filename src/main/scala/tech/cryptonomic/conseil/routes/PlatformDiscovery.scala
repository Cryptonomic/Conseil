package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.JsonUtil._
import tech.cryptonomic.conseil.util.{ConfigUtil, RouteHandling}

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object PlatformDiscovery {
  def apply(config:  PlatformsConfiguration)(implicit apiExecutionContext: ExecutionContext): PlatformDiscovery = new PlatformDiscovery(config)
}

/**
  * Platform discovery routes.
  *
  * @param config              configuration object
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class PlatformDiscovery(config: PlatformsConfiguration)(implicit apiExecutionContext: ExecutionContext) extends LazyLogging with RouteHandling {
  val route: Route =
    get {
      pathPrefix("platforms") {
        complete(toJson(ConfigUtil.getPlatforms(config)))
      } ~
        pathPrefix(Segment) { platform =>
          pathPrefix("networks") {
            pathEnd {
              complete(toJson(ConfigUtil.getNetworks(config, platform)))
            }
          } ~ pathPrefix(Segment) { network =>
            validatePlatformAndNetwork(config, platform, network) {
              pathPrefix("entities") {
                pathEnd {
                  completeWithJson(TezosPlatformDiscoveryOperations.getEntities(network))
                }
              } ~ pathPrefix(Segment) { entity =>
                validateEntity(entity) {
                  pathPrefix("attributes") {
                    pathEnd {
                      completeWithJson(TezosPlatformDiscoveryOperations.getTableAttributes(entity))
                    }
                  } ~ pathPrefix(Segment) { attribute =>
                    validateAttributes(entity, attribute) {
                      pathEnd {
                        completeWithJson(TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute))
                      } ~ pathPrefix(Segment) { filter =>
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
