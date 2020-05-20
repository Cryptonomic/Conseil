package tech.cryptonomic.conseil.api.routes.platform.discovery

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import endpoints.akkahttp
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.AttributesValidationError
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}

/** Companion object providing apply implementation */
object PlatformDiscovery {
  def apply(metadataService: MetadataService): PlatformDiscovery =
    new PlatformDiscovery(metadataService)
}

/**
  * Platform discovery routes.
  *
  * @param metadataService     metadata Service
  */
class PlatformDiscovery(metadataService: MetadataService)
    extends LazyLogging
    with PlatformDiscoveryEndpoints
    with akkahttp.server.Endpoints
    with akkahttp.server.JsonSchemaEntities {

  /** Metadata route implementation for platforms endpoint */
  private lazy val platformsRoute = platformsEndpoint.implementedBy(_ => metadataService.getPlatforms)

  /** Metadata route implementation for networks endpoint */
  private lazy val networksRoute = networksEndpoint.implementedBy {
    case (platform, _) => metadataService.getNetworks(PlatformPath(platform))
  }

  /** Metadata route implementation for entities endpoint */
  private lazy val entitiesRoute = entitiesEndpoint.implementedByAsync {
    case (platform, network, _) => metadataService.getCurrentEntities(NetworkPath(network, PlatformPath(platform)))
  }

  /** Metadata route implementation for attributes endpoint */
  private lazy val attributesRoute = attributesEndpoint.implementedByAsync {
    case ((platform, network, entity), _) =>
      metadataService.getCurrentTableAttributes(EntityPath(entity, NetworkPath(network, PlatformPath(platform))))
  }

  /** Metadata route implementation for attributes values endpoint */
  private lazy val attributesValuesRoute = attributesValuesEndpoint.implementedByAsync {
    case ((platform, network, entity), attribute, _) =>
      metadataService.getAttributeValues(platform, network, entity, attribute)
  }

  /** Metadata route implementation for attributes values with filter endpoint */
  private lazy val attributesValuesWithFilterRoute = attributesValuesWithFilterEndpoint.implementedByAsync {
    case (((platform, network, entity), attribute, filter), _) =>
      metadataService.getAttributeValues(platform, network, entity, attribute, Some(filter))
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

  override def validatedAttributes[A](
      response: A => Route,
      invalidDocs: Documentation
  ): Either[List[AttributesValidationError], A] => Route = {
    case Left(errors) =>
      complete(StatusCodes.BadRequest -> s"Errors: \n${errors.mkString("\n")}")
    case Right(success) =>
      response(success)
  }
}
