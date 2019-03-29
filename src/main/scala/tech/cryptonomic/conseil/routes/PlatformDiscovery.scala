package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import endpoints.akkahttp
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.routes.openapi.PlatformDiscoveryEndpoints
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.ConfigUtil
import tech.cryptonomic.conseil.util.ConfigUtil.getNetworks

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object PlatformDiscovery {
  def apply(platforms: PlatformsConfiguration, tezosPlatformDiscoveryOperations: TezosPlatformDiscoveryOperations)(
      implicit apiExecutionContext: ExecutionContext
  ): PlatformDiscovery =
    new PlatformDiscovery(platforms, tezosPlatformDiscoveryOperations)(apiExecutionContext)
}

/**
  * Platform discovery routes.
  *
  * @param config              configuration object
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class PlatformDiscovery(
    config: PlatformsConfiguration,
    tezosPlatformDiscoveryOperations: TezosPlatformDiscoveryOperations
)(implicit apiExecutionContext: ExecutionContext)
    extends LazyLogging
    with PlatformDiscoveryEndpoints
    with akkahttp.server.Endpoints
    with akkahttp.server.JsonSchemaEntities {

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
      tezosPlatformDiscoveryOperations.getEntities.map { entities =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          entities
        }
      }
  }

  /** Metadata route implementation for attributes endpoint */
  private val attributesRoute = attributesEndpoint.implementedByAsync {
    case ((platform, network, entity), _) =>
      tezosPlatformDiscoveryOperations.getTableAttributes(entity).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).flatMap { _ =>
          attributes
        }
      }
  }

  /** Metadata route implementation for attributes values endpoint */
  private val attributesValuesRoute = attributesValuesEndpoint.implementedByAsync {
    case ((platform, network, entity), attribute, _) =>
      tezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          attributes
        }
      }
  }

  /** Metadata route implementation for attributes values with filter endpoint */
  private val attributesValuesWithFilterRoute = attributesValuesWithFilterEndpoint.implementedByAsync {
    case (((platform, network, entity), attribute, filter), _) =>
      tezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute, Some(filter)).map { attributes =>
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          attributes
        }
      }
  }

  /** Concatenated metadata routes */
  val route: Route =
    concat(
      platformsRoute,
      networksRoute,
      entitiesRoute,
      attributesRoute,
      attributesValuesRoute,
      attributesValuesWithFilterRoute
    )
}
