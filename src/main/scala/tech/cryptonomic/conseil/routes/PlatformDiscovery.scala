package tech.cryptonomic.conseil.routes

import akka.http.caching.LfuCache
import akka.http.caching.scaladsl.{Cache, CachingSettings}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import com.typesafe.scalalogging.LazyLogging
import endpoints.akkahttp
import tech.cryptonomic.conseil.config.HttpCacheConfiguration
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.routes.openapi.PlatformDiscoveryEndpoints
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.ConfigUtil.getNetworks
import tech.cryptonomic.conseil.util.{ConfigUtil, RouteHandling}
import akka.http.scaladsl.server.Directives._


import scala.concurrent.{ExecutionContext, Future}

/** Companion object providing apply implementation */
object PlatformDiscovery {
  def apply(platforms: PlatformsConfiguration, caching: HttpCacheConfiguration)(implicit apiExecutionContext: ExecutionContext): PlatformDiscovery =
    new PlatformDiscovery(platforms, caching)(apiExecutionContext)
}

/**
  * Platform discovery routes.
  *
  * @param config              configuration object
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class PlatformDiscovery(config: PlatformsConfiguration, caching: HttpCacheConfiguration)(implicit apiExecutionContext: ExecutionContext)
  extends LazyLogging with RouteHandling with PlatformDiscoveryEndpoints with akkahttp.server.Endpoints with akkahttp.server.JsonSchemaEntities {

  val platformsRoute = platformsEndpoint.implementedBy(_ => ConfigUtil.getPlatforms(config))
  val networksRoute = networksEndpoint.implementedBy {
    case (platform, _) =>
      val networks = getNetworks(config, platform)
      if (networks.isEmpty)
        None
      else Some(networks)
  }
  val entitiesRoute = entitiesEndpoint.implementedByAsync {
    case (platform, network, _) =>
      TezosPlatformDiscoveryOperations.getEntities.map { entities =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          entities
        }
      }
  }

  val attributesRoute = attributesEndpoint.implementedByAsync {
    case ((platform, network, entity), _) =>
      TezosPlatformDiscoveryOperations.getTableAttributes(entity).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          attributes
        }
      }
  }

  val attributesValuesRoute = attributesValuesEndpoint.implementedByAsync {
    case ((platform, network, entity), attribute, _) =>
      TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          attributes
        }
      }
  }

  val attributesValuesWithFilterRoute = attributesValuesWithFilterEndpoint.implementedByAsync {
    case (((platform, network, entity), attribute, filter), _) =>
      TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute, Some(filter)).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          attributes
        }
      }
  }

  val route: Route = concat(
    platformsRoute,
    networksRoute,
    entitiesRoute,
    attributesRoute,
    attributesValuesRoute,
    attributesValuesWithFilterRoute
  )

  /** default caching settings */
  private val defaultCachingSettings: CachingSettings = CachingSettings(caching.cacheConfig)
  /** simple partial function for filtering */
  private val requestCacheKeyer: PartialFunction[RequestContext, Uri] = {
    case r: RequestContext => r.request.uri
  }
  /** LFU caching settings */
  private val cachingSettings: CachingSettings =
    defaultCachingSettings.withLfuCacheSettings(defaultCachingSettings.lfuCacheSettings)
  /** LFU cache */
  private val lfuCache: Cache[Uri, RouteResult] = LfuCache(cachingSettings)

  private def platformNetworkValidation[A](platform: String, network: String)(operation: Future[A]): Future[Option[A]] = {
    operation.map { xxx =>
      ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
        xxx
      }
    }
  }

  //
  //  /** Metadata route */
  //  val route: Route =
  //    get {
  //      cache(lfuCache, requestCacheKeyer) {
  //        pathPrefix("platforms") {
  //          complete(toJson(ConfigUtil.getPlatforms(config)))
  //        } ~
  //          pathPrefix(Segment) { platform =>
  //            pathPrefix("networks") {
  //              validatePlatform(config, platform) {
  //                pathEnd {
  //                  complete(toJson(ConfigUtil.getNetworks(config, platform)))
  //                }
  //              }
  //            } ~ pathPrefix(Segment) { network =>
  //              validatePlatformAndNetwork(config, platform, network) {
  //                pathPrefix("entities") {
  //                  pathEnd {
  //                    completeWithJson(TezosPlatformDiscoveryOperations.getEntities)
  //                  }
  //                } ~ pathPrefix(Segment) { entity =>
  //                  validateEntity(entity) {
  //                    pathPrefix("attributes") {
  //                      pathEnd {
  //                        completeWithJson(TezosPlatformDiscoveryOperations.getTableAttributes(entity))
  //                      }
  //                    } ~ pathPrefix(Segment) { attribute =>
  //                      validateAttributes(entity, attribute) {
  //                        pathEnd {
  //                          completeWithJson(TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute))
  //                        } ~ pathPrefix(Segment) { filter =>
  //                          pathEnd {
  //                            completeWithJson(TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute, Some(filter)))
  //                          }
  //                        }
  //                      }
  //                    }
  //                  }
  //                }
  //              }
  //            }
  //          }
  //      }
  //    }
}
