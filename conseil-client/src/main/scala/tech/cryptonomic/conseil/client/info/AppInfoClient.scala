package tech.cryptonomic.conseil.client.info

import tech.cryptonomic.conseil.api.routes.info.AppInfoEndpoint
// import tech.cryptonomic.conseil.api.routes.info.AppInfo.Info

import endpoints4s.xhr.JsonEntitiesFromSchemas
import endpoints4s.xhr.thenable._

object AppInfoClient extends AppInfoEndpoint with Endpoints with JsonEntitiesFromSchemas {

  val eventuallyInfo = appInfoEndpoint(Some(String.empty))
}
