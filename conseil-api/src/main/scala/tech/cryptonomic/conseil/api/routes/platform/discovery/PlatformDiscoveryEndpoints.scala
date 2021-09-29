package tech.cryptonomic.conseil.api.routes.platform.discovery

import endpoints4s.algebra
import tech.cryptonomic.conseil.api.routes.validation.AttributesQueryValidation
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.AttributesValidationError
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes

/** Trait containing platform discovery endpoints definition */
trait PlatformDiscoveryEndpoints
    extends algebra.Endpoints
    with algebra.JsonEntitiesFromSchemas
    with PlatformDiscoveryJsonSchemas
    with AttributesQueryValidation {

  /** Common path for metadata endpoints */
  private val commonPath = path / "v2" / "metadata"

  private val commonDocs = EndpointDocs(tags = List("Metadata"))

  /** Metadata platforms endpoint */
  def platformsEndpoint: Endpoint[Option[String], List[PlatformDiscoveryTypes.Platform]] =
    endpoint(
      request = get(url = commonPath / "platforms", headers = optRequestHeader("apiKey")),
      response = ok(
        jsonResponse[List[PlatformDiscoveryTypes.Platform]],
        docs = Some("Metadata endpoint for listing available platforms")
      ),
      docs = commonDocs
    )

  /** Metadata networks endpoint */
  def networksEndpoint: Endpoint[(String, Option[String]), Option[List[PlatformDiscoveryTypes.Network]]] =
    endpoint(
      request =
        get(url = commonPath / segment[String](name = "platform") / "networks", headers = optRequestHeader("apiKey")),
      response = ok(
        jsonResponse[List[PlatformDiscoveryTypes.Network]],
        docs = Some("Metadata endpoint for listing available networks")
      ).orNotFound(Some("Not found")),
      docs = commonDocs
    )

  /** Metadata entities endpoint */
  def entitiesEndpoint: Endpoint[(String, String, Option[String]), Option[List[PlatformDiscoveryTypes.Entity]]] =
    endpoint(
      request = get(
        url = commonPath / segment[String](name = "platform") / segment[String](name = "network") / "entities",
        headers = optRequestHeader("apiKey")
      ),
      response = ok(
        jsonResponse[List[PlatformDiscoveryTypes.Entity]],
        docs = Some("Metadata endpoint for listing available entities")
      ).orNotFound(Some("Not found")),
      docs = commonDocs
    )

  /** Metadata attributes endpoint */
  def attributesEndpoint: Endpoint[((String, String, String), Option[String]), Option[
    List[PlatformDiscoveryTypes.Attribute]
  ]] =
    endpoint(
      request = get(
        url = commonPath / segment[String](name = "platform") / segment[String](name = "network") / segment[String](
                name = "entity"
              ) / "attributes",
        headers = optRequestHeader("apiKey")
      ),
      response = ok(
        jsonResponse[List[PlatformDiscoveryTypes.Attribute]],
        docs = Some("Metadata endpoint for listing available attributes")
      ).orNotFound(Some("Not found")),
      docs = commonDocs
    )

  /** Metadata attributes values endpoint */
  def attributesValuesEndpoint: Endpoint[((String, String, String, String), Option[String]), Option[
    Either[List[AttributesValidationError], List[String]]
  ]] =
    endpoint(
      request = get(
        url = commonPath / segment[String](name = "platform") / segment[String](name = "network") / segment[String](
                name = "entity"
              ) / segment[String](name = "attribute"),
        headers = optRequestHeader("apiKey")
      ),
      response = validatedAttributes[List[String]](
        response = ok(
          jsonResponse[List[String]],
          docs = Some("Metadata endpoint for listing available distinct values for given attribute")
        ),
        invalidDocs = Some("Cannot get the attributes!")
      ).orNotFound(Some("Not found")),
      docs = commonDocs
    )

  /** Metadata attributes values with filter endpoint */
  def attributesValuesWithFilterEndpoint: Endpoint[((String, String, String, String, String), Option[String]), Option[
    Either[List[AttributesValidationError], List[String]]
  ]] =
    endpoint(
      request = get(
        url = commonPath / segment[String](name = "platform") / segment[String](name = "network") / segment[String](
                name = "entity"
              ) / segment[String](name = "attribute") / segment[String](name = "filter"),
        headers = optRequestHeader("apiKey")
      ),
      response = validatedAttributes[List[String]](
        response = ok(
          jsonResponse[List[String]],
          docs = Some("Metadata endpoint for listing available distinct values for given attribute filtered")
        ),
        invalidDocs = Some("Cannot get the attributes!")
      ).orNotFound(Some("Not found")),
      docs = commonDocs
    )

}
