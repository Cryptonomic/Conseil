package tech.cryptonomic.conseil.client.platform.data

import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataEndpoints

import endpoints4s.xhr.JsonEntitiesFromSchemas
import endpoints4s.xhr.thenable._

object ApiDataClient extends ApiDataEndpoints with Endpoints with JsonEntitiesFromSchemas {

  private val platform: String = ???
  val eventuallyQuery = queryEndpoint(platform)(String.empty, String.empty, ApiQuery, Some(String.empty))
}
