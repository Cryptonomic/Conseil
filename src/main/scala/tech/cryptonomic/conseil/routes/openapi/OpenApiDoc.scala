package tech.cryptonomic.conseil.routes.openapi

import cats.Functor
import endpoints.algebra.Documentation
import endpoints.openapi
import endpoints.openapi.model.{Info, MediaType, OpenApi, Schema}

/** OpenAPI documentation object */
object OpenApiDoc extends DataEndpoints
  with PlatformDiscoveryEndpoints
  with TezosEndpoints
  with AppInfoEndpoint
  with openapi.model.OpenApiSchemas
  with openapi.JsonSchemaEntities
  with openapi.BasicAuthentication {

  /** OpenAPI definition */
  def openapi: OpenApi = openApi(Info("Conseil API", "0.0.1"))(
    queryEndpoint,
    blocksEndpoint,
    blocksHeadEndpoint,
    blockByHashEndpoint,
    accountsEndpoint,
    accountByIdEndpoint,
    operationGroupsEndpoint,
    operationGroupByIdEndpoint,
    avgFeesEndpoint,
    operationsEndpoint,
    platformsEndpoint,
    networksEndpoint,
    entitiesEndpoint,
    attributesEndpoint,
    attributesValuesEndpoint,
    attributesValuesWithFilterEndpoint,
    blocksEndpointV1,
    blocksHeadEndpointV1,
    blockByHashEndpointV1,
    accountsEndpointV1,
    accountByIdEndpointV1,
    operationGroupsEndpointV1,
    operationGroupByIdEndpointV1,
    avgFeesEndpointV1,
    operationsEndpointV1,
    appInfoEndpoint
  )

  /** Function for validation definition in documentation which appends DocumentedResponse to the list of possible results from the query.
    * In this case if query fails to validate it will return 400 Bad Request.
    * */
  override def validated[A](response: List[OpenApiDoc.DocumentedResponse], invalidDocs: Documentation): List[OpenApiDoc.DocumentedResponse] =
    response :+ OpenApiDoc.DocumentedResponse(
      status = 400,
      documentation = invalidDocs.getOrElse(""),
      content = Map(
        "application/json" -> MediaType(schema = Some(Schema.Array(Schema.simpleString, None)))
      )
    ) :+ OpenApiDoc.DocumentedResponse(
      status = 200,
      documentation = invalidDocs.getOrElse(""),
      content = Map(
        "application/json" -> MediaType(None),
        "text/csv" -> MediaType(None)
      )
    )

  /** Documented JSON schema for Any */
  override implicit def anySchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("Any - not yet supported")

  /** Documented JSON schema for query response */
  override implicit def queryResponseSchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("Any - not yet supported")

  /** Documented query string for functor */
  override implicit def qsFunctor: Functor[QueryString] = new Functor[QueryString] {
    override def map[From, To](f: OpenApiDoc.DocumentedQueryString)(map: From => To): OpenApiDoc.DocumentedQueryString = f
  }

  override implicit def queryResponseSchemaWithOutputType: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("Any - not yet supported")

  override def validatedAttributes[A](response: List[OpenApiDoc.DocumentedResponse], invalidDocs: Documentation): List[OpenApiDoc.DocumentedResponse] =
    response :+ OpenApiDoc.DocumentedResponse(
      status = 400,
      documentation = invalidDocs.getOrElse(""),
      content = Map(
        "application/json" -> MediaType(schema = Some(Schema.Array(Schema.simpleString, None)))
      )
    )
}
