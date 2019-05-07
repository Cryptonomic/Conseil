package tech.cryptonomic.conseil.routes

import tech.cryptonomic.conseil.routes.openapi.OpenApiDoc
import endpoints.akkahttp.server
import endpoints.openapi.model

/** implements a server endpoint to provide
  * the openapi definition as a json file
  */
object Docs
  extends server.Endpoints
  with model.OpenApiSchemas
  with server.JsonSchemaEntities {

  val route = endpoint(
    request = get(
      url = path / "openapi.json"
    ),
    response = jsonResponse[model.OpenApi]()
  ).implementedBy(_ => OpenApiDoc.openapi)
}
