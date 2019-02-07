package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra.Documentation
import endpoints.openapi
import endpoints.openapi.model.{Info, MediaType, OpenApi, Schema}
import io.circe.Json
import io.circe.syntax._

object OpenApiDoc
  extends Endpoints
    with openapi.Endpoints
    with openapi.JsonSchemaEntities
    with openapi.BasicAuthentication {

  def openapi: OpenApi = openApi(Info("Conseil API", "0.0.1"))(queryEndpoint, blocksEndpoint)

  def openapiJson: Json =
    openapi.asJson

  def validated[A](response: List[OpenApiDoc.DocumentedResponse], invalidDocs: Documentation): List[OpenApiDoc.DocumentedResponse] =
    response :+ OpenApiDoc.DocumentedResponse(
      status = 400,
      documentation = invalidDocs.getOrElse(""),
      content = Map(
        "application/json" -> MediaType(schema = Some(Schema.Array(Schema.simpleString)))
      )
    )

  override implicit def anySchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("any")


  override implicit def queryResponseSchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("any")

}
