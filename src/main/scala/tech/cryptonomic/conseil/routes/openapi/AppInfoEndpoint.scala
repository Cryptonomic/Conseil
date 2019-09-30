package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra
import tech.cryptonomic.conseil.routes.AppInfo.Info

/** Trait containing AppInfo endpoint definition */
trait AppInfoEndpoint extends algebra.Endpoints with algebra.JsonSchemaEntities with AppInfoJsonSchemas {

  /** AppInfo endpoint definition */
  def appInfoEndpoint: Endpoint[Option[String], Info] = endpoint(
    request = get(url = path / "info", headers = optHeader("apiKey")),
    response = jsonResponse[Info](docs = Some("Application info endpoint")),
    tags = List("App Info")
  )
}
