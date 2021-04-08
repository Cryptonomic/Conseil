package tech.cryptonomic.conseil.api.routes.info

import endpoints.algebra
import AppInfo.Info

/** Trait containing AppInfo endpoint definition */
trait AppInfoEndpoint extends algebra.Endpoints with algebra.JsonEntitiesFromSchemas with AppInfoJsonSchemas {

  /** AppInfo endpoint definition */
  def appInfoEndpoint: Endpoint[Option[String], Info] = endpoint(
    request = get(url = path / "info", headers = optRequestHeader("apiKey")),
    response = ok(jsonResponse[Info], docs = Some("Application info endpoint")),
    docs = EndpointDocs(tags = List("App Info"))
  )
}
