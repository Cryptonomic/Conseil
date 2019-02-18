package tech.cryptonomic.conseil.routes.openapi

import cats.Functor
import endpoints.algebra.Documentation
import endpoints.openapi
import endpoints.openapi.model._
import io.circe.Json
import io.circe.syntax._

object OpenApiDoc
  extends DataEndpoints
    with openapi.Endpoints
    with openapi.JsonSchemaEntities
    with openapi.BasicAuthentication {

  def openapiJson: Json =
    openapi.asJson

  def openapi: OpenApi = openApi(Info("Conseil API", "0.0.1"))(queryEndpoint, blocksEndpoint, blocksHeadEndpoint, blockByHashEndpoint,
    accountsEndpoint, accountByIdEndpoint, operationGroupsEndpoint, operationGroupByIdEndpoint, avgFeesEndpoint, operationsEndpoint)

  def validated[A](response: List[OpenApiDoc.DocumentedResponse], invalidDocs: Documentation): List[OpenApiDoc.DocumentedResponse] =
    response :+ OpenApiDoc.DocumentedResponse(
      status = 400,
      documentation = invalidDocs.getOrElse(""),
      content = Map(
        "application/json" -> MediaType(schema = Some(Schema.Array(Schema.simpleString)))
      )
    )

  override implicit def anySchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("Any - not yet supported")

  override implicit def queryResponseSchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("Any - not yet supported")

  override def qsList[A: QueryStringParam](name: String, docs: Option[String]): DocumentedQueryString = new DocumentedQueryString(
    List(
      DocumentedParameter(name, false, docs, Schema.Array(implicitly[QueryStringParam[A]]))
    )
  )

  override implicit def qsFunctor: Functor[QueryString] = new Functor[QueryString] {
    override def map[From, To](f: OpenApiDoc.DocumentedQueryString)(map: From => To): OpenApiDoc.DocumentedQueryString = f
  }

  override implicit def timestampSchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("integer")

  override implicit def blocksByHashSchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("Any - not yet supported")
}
