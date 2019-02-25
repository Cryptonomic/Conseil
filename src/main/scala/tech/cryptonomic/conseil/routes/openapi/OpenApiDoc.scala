package tech.cryptonomic.conseil.routes.openapi

import cats.Functor
import endpoints.algebra.Documentation
import endpoints.openapi
import endpoints.openapi.model._
import io.circe.Json
import io.circe.syntax._

/** OpenAPI documentation object */
object OpenApiDoc extends DataEndpoints
  with PlatformDiscoveryEndpoints
  with TezosEndpoints
  with openapi.Endpoints
  with openapi.JsonSchemaEntities
  with openapi.BasicAuthentication {

  /** OpenAPI JSON*/
  def openapiJson: Json =
    openapi.asJson

  /** OpenAPI definition */
  def openapi: OpenApi = openApi(Info("Conseil API", "0.0.1"))(queryEndpoint, blocksEndpoint, blocksHeadEndpoint, blockByHashEndpoint,
    accountsEndpoint, accountByIdEndpoint, operationGroupsEndpoint, operationGroupByIdEndpoint, avgFeesEndpoint, operationsEndpoint,
    platformsEndpoint, networksEndpoint, entitiesEndpoint, attributesEndpoint, attributesValuesEndpoint, attributesValuesWithFilterEndpoint,
    blocksEndpointV1, blocksHeadEndpointV1, blockByHashEndpointV1, accountsEndpointV1, accountByIdEndpointV1, operationGroupsEndpointV1,
    operationGroupByIdEndpointV1, avgFeesEndpointV1, operationsEndpointV1
  )

  /** Function for validation definition in documentation */
  def validated[A](response: List[OpenApiDoc.DocumentedResponse], invalidDocs: Documentation): List[OpenApiDoc.DocumentedResponse] =
    response :+ OpenApiDoc.DocumentedResponse(
      status = 400,
      documentation = invalidDocs.getOrElse(""),
      content = Map(
        "application/json" -> MediaType(schema = Some(Schema.Array(Schema.simpleString)))
      )
    )

  /** Documented JSON schema for Any */
  override implicit def anySchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("Any - not yet supported")

  /** Documented JSON schema for query response */
  override implicit def queryResponseSchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("Any - not yet supported")

  /** Documented query string for query string list */
  override def qsList[A: QueryStringParam](name: String, docs: Option[String]): DocumentedQueryString = new DocumentedQueryString(
    List(
      DocumentedParameter(name, false, docs, Schema.Array(implicitly[QueryStringParam[A]]))
    )
  )

  /** Documented query string for functor */
  override implicit def qsFunctor: Functor[QueryString] = new Functor[QueryString] {
    override def map[From, To](f: OpenApiDoc.DocumentedQueryString)(map: From => To): OpenApiDoc.DocumentedQueryString = f
  }

  /** Documented JSON schema for timestamp */
  override implicit def timestampSchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("integer")

  /** Documented JSON schema for blocks by hash */
  override implicit def blocksByHashSchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("Any - not yet supported")
}
