package tech.cryptonomic.conseil.api.routes.info

import endpoints.algebra
import AppInfo.Info

/** Trait containing AppInfo endpoint definition */
trait AppInfoEndpoint extends algebra.Endpoints with algebra.JsonSchemaEntities with AppInfoJsonSchemas {

  /** AppInfo endpoint definition */
  def appInfoEndpoint: Endpoint[Option[String], Info] = endpoint(
    request = get(url = path / "info", headers = optHeader("apiKey")),
    response = jsonResponse[Info](docs = Some("Application info endpoint")),
    tags = List("App Info")
  )
}
