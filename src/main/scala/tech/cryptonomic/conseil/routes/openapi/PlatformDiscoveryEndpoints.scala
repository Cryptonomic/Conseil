package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes

trait PlatformDiscoveryEndpoints
  extends algebra.Endpoints
    with PlatformDiscoveryJsonSchemas
    with algebra.JsonSchemaEntities {

  private val commonPath = path / "v2" / "metadata"

  def platformsEndpoint =
    endpoint(
      request = get(
        url = commonPath / "platforms" ,
        headers = header("apiKey")),
      response = jsonResponse[List[PlatformDiscoveryTypes.Platform]](docs = Some("Metadata endpoint for listing available platforms"))
    )

  def networksEndpoint =
    endpoint(
      request = get(
        url = commonPath / segment[String](name = "platforms") / "networks",
        headers = header("apiKey")),
      response = jsonResponse[List[PlatformDiscoveryTypes.Network]](docs = Some("Metadata endpoint for listing available networks")).orNotFound(Some("Not found"))
    )

  def entitiesEndpoint =
    endpoint(
      request = get(
        url = commonPath / segment[String](name = "platform") / segment[String](name = "network") / "entities",
        headers = header("apiKey")),
      response = jsonResponse[List[PlatformDiscoveryTypes.Entity]](docs = Some("Metadata endpoint for listing available entities")).orNotFound(Some("Not found"))
    )

  def attributesEndpoint =
    endpoint(
      request = get(
        url = commonPath / segment[String](name = "platform") / segment[String](name = "network") / segment[String](name = "entity") / "attributes",
        headers = header("apiKey")),
      response = jsonResponse[List[PlatformDiscoveryTypes.Attribute]](docs = Some("Metadata endpoint for listing available attributes")).orNotFound(Some("Not found"))
    )

  def attributesValuesEndpoint =
    endpoint(
      request = get(
        url = commonPath / segment[String](name = "platform") / segment[String](name = "network") / segment[String](name = "entity") / segment[String](name = "attribute"),
        headers = header("apiKey")),
      response = jsonResponse[List[String]](docs = Some("Metadata endpoint for listing available distinct values for given attribute")).orNotFound(Some("Not found"))
    )

  def attributesValuesWithFilterEndpoint =
    endpoint(
      request = get(
        url = commonPath / segment[String](name = "platform") / segment[String](name = "network") / segment[String](name = "entity") / segment[String](name = "attribute") / segment[String](name = "filter"),
        headers = header("apiKey")),
      response = jsonResponse[List[String]](docs = Some("Metadata endpoint for listing available distinct values for given attribute filtered")).orNotFound(Some("Not found"))
    )

}
