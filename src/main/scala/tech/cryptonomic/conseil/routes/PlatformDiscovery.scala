package tech.cryptonomic.conseil.routes

import akka.actor.ActorSystem
import akka.http.caching.LfuCache
import akka.http.caching.scaladsl.{Cache, CachingSettings}
import akka.http.scaladsl.model.{HttpMethods, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.CachingDirectives._
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.JsonUtil._
import tech.cryptonomic.conseil.util.{ConfigUtil, RouteHandling}

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object PlatformDiscovery {
  def apply(config:  PlatformsConfiguration)(implicit apiExecutionContext: ExecutionContext, system: ActorSystem): PlatformDiscovery =
    new PlatformDiscovery(config)
}

/**
  * Platform discovery routes.
  *
  * @param config              configuration object
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class PlatformDiscovery(config: PlatformsConfiguration)(implicit apiExecutionContext: ExecutionContext, system: ActorSystem) extends LazyLogging with RouteHandling {
  val defaultCachingSettings: CachingSettings = CachingSettings(system)

  val simpleKeyer: PartialFunction[RequestContext, Uri] = {
    case r: RequestContext if r.request.method == HttpMethods.GET => r.request.uri
  }
  val cachingSettings: CachingSettings =
    defaultCachingSettings.withLfuCacheSettings(defaultCachingSettings.lfuCacheSettings)

  val lfuCache: Cache[Uri, RouteResult] = LfuCache(cachingSettings)

  val route: Route =
    get {
      cache(lfuCache, simpleKeyer) {
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
}
