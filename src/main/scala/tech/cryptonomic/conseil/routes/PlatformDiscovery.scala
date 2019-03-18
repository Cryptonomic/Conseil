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
import tech.cryptonomic.conseil.generic.chain.MetadataDiscovery

import scala.concurrent.ExecutionContext

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

  /** Metadata route implementation for platforms endpoint */
  private val platformsRoute = platformsEndpoint.implementedBy(_ => ConfigUtil.getPlatforms(config))

  /** Metadata route implementation for networks endpoint */
  private val networksRoute = networksEndpoint.implementedBy {
    case (platform, _) =>
      val networks = getNetworks(config, platform)
      if (networks.isEmpty) None
      else Some(networks)
  }

  /** Metadata route implementation for entities endpoint */
  private val entitiesRoute = entitiesEndpoint.implementedByAsync {
    case (platform, network, _) =>
      MetadataDiscovery.getEntities.map { entities =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          entities
        }
      }
  }

  /** Metadata route implementation for attributes endpoint */
  private val attributesRoute = attributesEndpoint.implementedByAsync {
    case ((platform, network, entity), _) =>
      //TezosPlatformDiscoveryOperations.getTableAttributes(entity).map { attributes =>
      MetadataDiscovery.getAttributes(entity).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).flatMap { _ =>
          attributes
        }
      }
  }

  /** Metadata route implementation for attributes values endpoint */
  private val attributesValuesRoute = attributesValuesEndpoint.implementedByAsync {
    case ((platform, network, entity), attribute, _) =>
      TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          attributes
        }
      }
  }

  /** Metadata route implementation for attributes values with filter endpoint */
  private val attributesValuesWithFilterRoute = attributesValuesWithFilterEndpoint.implementedByAsync {
    case (((platform, network, entity), attribute, filter), _) =>
      TezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute, Some(filter)).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          attributes
        }
      }
  }

  /** Concatenated metadata routes with cache */
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
