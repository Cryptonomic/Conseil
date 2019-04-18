package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import endpoints.akkahttp
import tech.cryptonomic.conseil.metadata.{EntityPath, MetadataService, NetworkPath, PlatformPath}
import tech.cryptonomic.conseil.routes.openapi.PlatformDiscoveryEndpoints

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object PlatformDiscovery {
  def apply(metadataService: MetadataService)(implicit apiExecutionContext: ExecutionContext): PlatformDiscovery =
    new PlatformDiscovery(metadataService)
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
    case (platform, _) => metadataService.getNetworks(PlatformPath(platform))
  }

  /** Metadata route implementation for entities endpoint */
  private val entitiesRoute = entitiesEndpoint.implementedByAsync {
    case (platform, network, _) => metadataService.getEntities(NetworkPath(network, PlatformPath(platform)))
  }

  /** Metadata route implementation for attributes endpoint */
  private val attributesRoute = attributesEndpoint.implementedByAsync {
    case ((platform, network, entity), _) => metadataService.getTableAttributes(EntityPath(entity, NetworkPath(network, PlatformPath(platform))))
  }

  /** Metadata route implementation for attributes values endpoint */
  private val attributesValuesRoute = attributesValuesEndpoint.implementedByAsync {
    case ((platform, network, entity), attribute, _) => metadataService.getAttributeValues(platform, network, entity, attribute)
  }

  /** Metadata route implementation for attributes values with filter endpoint */
  private val attributesValuesWithFilterRoute = attributesValuesWithFilterEndpoint.implementedByAsync {
    case (((platform, network, entity), attribute, filter), _) => metadataService.getAttributeValues(platform, network, entity, attribute, Some(filter))
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
