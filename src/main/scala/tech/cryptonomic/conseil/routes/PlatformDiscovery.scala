package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import endpoints.akkahttp
import tech.cryptonomic.conseil.metadata.MetadataService
import tech.cryptonomic.conseil.routes.openapi.PlatformDiscoveryEndpoints

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object PlatformDiscovery {
  def apply(metadataService: MetadataService)(implicit apiExecutionContext: ExecutionContext): PlatformDiscovery =
    new PlatformDiscovery(metadataService)(apiExecutionContext)
}

/**
  * Platform discovery routes.
  *
  * @param metadataService     metadata Service
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class PlatformDiscovery(metadataService: MetadataService)(implicit apiExecutionContext: ExecutionContext)
  extends LazyLogging with PlatformDiscoveryEndpoints with akkahttp.server.Endpoints with akkahttp.server.JsonSchemaEntities {

  /** Metadata route implementation for platforms endpoint */
  private val platformsRoute = platformsEndpoint.implementedBy(_ => metadataService.getPlatforms)

  /** Metadata route implementation for networks endpoint */
  private val networksRoute = networksEndpoint.implementedBy {
    case (platform, _) =>
      val networks = metadataService.getNetworks(platform)
      if (networks.isEmpty) None
      else Some(networks)
  }

  /** Metadata route implementation for entities endpoint */
  private val entitiesRoute = entitiesEndpoint.implementedByAsync {
    case (platform, network, _) => metadataService.getEntities(platform, network)
  }

  /** Metadata route implementation for attributes endpoint */
  private val attributesRoute = attributesEndpoint.implementedByAsync {
    case ((platform, network, entity), _) => metadataService.getTableAttributes(platform, network, entity)
  }

  /** Metadata route implementation for attributes values endpoint */
  private val attributesValuesRoute = attributesValuesEndpoint.implementedByAsync {
    case ((platform, network, entity), attribute, _) => metadataService.getAttributeValues(platform, network, entity, attribute)
  }

  /** Metadata route implementation for attributes values with filter endpoint */
  private val attributesValuesWithFilterRoute = attributesValuesWithFilterEndpoint.implementedByAsync {
    case (((platform, network, entity), attribute, filter), _) => metadataService.getAttributesValuesWithFilterRoute(platform, network, entity, attribute, filter)
  }

  /** Concatenated metadataService routes */
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
