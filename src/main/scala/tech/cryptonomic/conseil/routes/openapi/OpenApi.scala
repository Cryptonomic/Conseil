package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra.Documentation
import endpoints.openapi
import endpoints.openapi.model.{Info, MediaType, OpenApi, Schema}
import io.circe.Json
import io.circe.syntax._

object OpenApi
  extends Endpoints
    with openapi.Endpoints
    with openapi.JsonSchemaEntities
    with openapi.BasicAuthentication {

  def openapi: OpenApi = openApi(Info("Conseil API", "0.0.1"))(queryEndpoint)

  def openapiJson: Json =
    openapi.asJson

  def validated[A](response: List[OpenApi.DocumentedResponse], invalidDocs: Documentation): List[OpenApi.DocumentedResponse] =
    response :+ OpenApi.DocumentedResponse(
      status = 401,
      documentation = invalidDocs.getOrElse(""),
      content = Map(
        "application/json" -> MediaType(schema = Some(Schema.Array(Schema.simpleString)))
      )
    )

  override implicit def anySchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("any")


  override implicit def queryResponseSchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("any")
}
