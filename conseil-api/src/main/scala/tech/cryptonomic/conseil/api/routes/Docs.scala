package tech.cryptonomic.conseil.api.routes

import tech.cryptonomic.conseil.api.routes.openapi.OpenApiDoc
import endpoints.akkahttp.server
import endpoints.openapi.model

/** implements a server endpoint to provide
  * the openapi definition as a json file
  */
object Docs extends server.Endpoints with model.OpenApiSchemas with server.circe.JsonSchemaEntities {

  val route = endpoint(
    request = get(
      url = path / "openapi.json"
    ),
    response = jsonResponse[model.OpenApi]()
  ).implementedBy(_ => OpenApiDoc.openapi)
}
