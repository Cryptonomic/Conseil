package tech.cryptonomic.conseil.client.platform.discovery

import tech.cryptonomic.conseil.api.routes.platform.discovery.PlatformDiscoveryEndpoints

import endpoints4s.xhr.JsonEntitiesFromSchemas
import endpoints4s.xhr.thenable._

object PlatformDiscoveryClient extends PlatformDiscoveryEndpoints with Endpoints with JsonEntitiesFromSchemas {

  val eventuallyPlatforms = platformsEndpoint(Some(String.empty))
  val eventuallyNetworks = networksEndpoint((String.empty, Some(String.empty)))
  val eventuallyEntities = entitiesEndpoint((String.empty, String.empty, Some(String.empty)))
  val eventuallyAttributes = attributesEndpoint((String.empty, String.empty) -> Some(String.empty))
  val eventuallyAttributesValues =
    attributesValuesEndpoint((String.empty, String.empty, String.empty) -> Some(String.empty))
  val eventuallyAttributesValuesWithFilter =
    attributesValuesWithFilterEndpoint((String.empty, String.empty, String.empty, String.empty) -> Some(String.empty))
}
