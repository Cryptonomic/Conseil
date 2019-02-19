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
import akka.http.scaladsl.server.directives.CachingDirectives._


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

  private val platformsRoute = platformsEndpoint.implementedBy(_ => ConfigUtil.getPlatforms(config))

  private val networksRoute = networksEndpoint.implementedBy {
    case (platform, _) =>
      val networks = getNetworks(config, platform)
      if (networks.isEmpty) None
      else Some(networks)
  }

  private val entitiesRoute = entitiesEndpoint.implementedByAsync {
    case (platform, network, _) =>
      TezosPlatformDiscoveryOperations.getEntities.map { entities =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          entities
        }
      }
  }

  private val attributesRoute = attributesEndpoint.implementedByAsync {
    case ((platform, network, entity), _) =>
      TezosPlatformDiscoveryOperations.getTableAttributes(entity).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          attributes
        }
      }
  }

  private val attributesValuesRoute = attributesValuesEndpoint.implementedByAsync {
    case ((platform, network, entity), attribute, _) =>
      TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          attributes
        }
      }
  }

  private val attributesValuesWithFilterRoute = attributesValuesWithFilterEndpoint.implementedByAsync {
    case (((platform, network, entity), attribute, filter), _) =>
      TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute, Some(filter)).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          attributes
        }
      }
  }

  val route: Route = cache(lfuCache, requestCacheKeyer) {
    concat(
      platformsRoute,
      networksRoute,
      entitiesRoute,
      attributesRoute,
      attributesValuesRoute,
      attributesValuesWithFilterRoute
    )
  }
}
