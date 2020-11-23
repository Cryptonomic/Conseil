package tech.cryptonomic.conseil.api.routes

import tech.cryptonomic.conseil.api.routes.openapi.OpenApiDoc
import endpoints.akkahttp.server
import endpoints.openapi.model.OpenApi

/** implements a server endpoint to provide
  * the openapi definition as a json file
  */
object Docs extends server.Endpoints with server.JsonEntitiesFromEncodersAndDecoders {

  val route = endpoint(
    request = get(url = path / "openapi.json"),
    response = ok(jsonResponse[OpenApi])
  ).implementedBy(_ => OpenApiDoc.openapi)

}
