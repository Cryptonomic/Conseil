package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.generic.chain.NetworkConfigOperations
import tech.cryptonomic.conseil.tezos.{ApiNetworkOperations, PlatformDiscoveryOperations}
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.JsonUtil._
import tech.cryptonomic.conseil.util.{ConfigUtil, RouteHandling}

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object PlatformDiscovery {
  def apply(apiNetworkOperations: ApiNetworkOperations, config: PlatformsConfiguration)(implicit apiExecutionContext: ExecutionContext): PlatformDiscovery =
    new PlatformDiscovery(apiNetworkOperations, config)
}

/**
  * Platform discovery routes.
  * @param apiNetworkOperations ApiNetworkOperations object
  * @param config configuration object
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class PlatformDiscovery(apiNetworkOperations: ApiNetworkOperations, config: PlatformsConfiguration)
  (implicit apiExecutionContext: ExecutionContext) extends LazyLogging with RouteHandling {
  import apiNetworkOperations._
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
              getApiOperations("tezos", network) { apiOperations =>
                val platformDiscoveryOperations = PlatformDiscoveryOperations(apiOperations)
                pathPrefix("entities") {
                  pathEnd {
                    completeWithJson(platformDiscoveryOperations.getEntities(network))
                  }
                } ~ pathPrefix(Segment) { entity =>
                  validateEntity(entity) {
                    pathPrefix("attributes") {
                      pathEnd {
                        completeWithJson(platformDiscoveryOperations.getTableAttributes(entity))
                      }
                    } ~ pathPrefix(Segment) { attribute =>
                      validateAttributes(entity, attribute) {
                        pathEnd {
                          completeWithJson(platformDiscoveryOperations.listAttributeValues(entity, attribute))
                        } ~ pathPrefix(Segment) { filter =>
                          pathEnd {
                            completeWithJson(platformDiscoveryOperations.listAttributeValues(entity, attribute, Some(filter)))
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
}
