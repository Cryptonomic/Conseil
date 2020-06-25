package tech.cryptonomic.conseil.api.routes.openapi

import cats.Functor
import endpoints.algebra.Documentation
import endpoints.openapi
import endpoints.openapi.model.{Info, MediaType, OpenApi, Schema}
import tech.cryptonomic.conseil.api.routes.info.AppInfoEndpoint
import tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.generic.BitcoinDataEndpoints
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.generic.TezosDataEndpoints
import tech.cryptonomic.conseil.api.routes.platform.discovery.PlatformDiscoveryEndpoints

/** OpenAPI documentation object */
object OpenApiDoc
    extends TezosDataEndpoints
    with BitcoinDataEndpoints
    with PlatformDiscoveryEndpoints
    with AppInfoEndpoint
    with openapi.model.OpenApiSchemas
    with openapi.JsonSchemaEntities
    with openapi.BasicAuthentication {

  /** OpenAPI definition */
  def openapi: OpenApi = openApi(Info("Conseil API", "0.0.1"))(
    queryEndpoint,
    tezosBlocksEndpoint,
    tezosBlocksHeadEndpoint,
    tezosBlockByHashEndpoint,
    tezosAccountsEndpoint,
    tezosAccountByIdEndpoint,
    tezosOperationGroupsEndpoint,
    tezosOperationGroupByIdEndpoint,
    tezosAvgFeesEndpoint,
    tezosOperationsEndpoint,
    bitcoinBlocksEndpoint,
    bitcoinBlocksHeadEndpoint,
    bitcoinBlockByHashEndpoint,
    bitcoinTransactionsEndpoint,
    bitcoinTransactionByIdEndpoint,
    bitcoinInputsEndpoint,
    bitcoinOutputsEndpoint,
    platformsEndpoint,
    networksEndpoint,
    entitiesEndpoint,
    attributesEndpoint,
    attributesValuesEndpoint,
    attributesValuesWithFilterEndpoint,
    appInfoEndpoint
  )

  /** Function for validation definition in documentation which appends DocumentedResponse to the list of possible results from the query.
    * In this case if query fails to validate it will return 400 Bad Request.
    * */
  override def validated[A](
      response: List[OpenApiDoc.DocumentedResponse],
      invalidDocs: Documentation
  ): List[OpenApiDoc.DocumentedResponse] =
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
            "text/csv" -> MediaType(None),
            "text/plain" -> MediaType(None)
          )
        )

  /** Documented JSON schema for Any */
  implicit override def anySchema: DocumentedJsonSchema = DocumentedJsonSchema.Primitive("Any - not yet supported")

  /** Documented JSON schema for query response */
  implicit override def queryResponseSchema: DocumentedJsonSchema =
    DocumentedJsonSchema.Primitive("Any - not yet supported")

  /** Query string functor adding map operation */
  implicit override def bitcoinQsFunctor: Functor[QueryString] = qsFunctor

  /** Query string functor adding map operation */
  implicit override def tezosQsFunctor: Functor[QueryString] = qsFunctor

  /** Documented query string for functor */
  private def qsFunctor: Functor[QueryString] = new Functor[QueryString] {
    override def map[From, To](f: DocumentedQueryString)(map: From => To): DocumentedQueryString = f
  }

  implicit override def queryResponseSchemaWithOutputType: DocumentedJsonSchema =
    DocumentedJsonSchema.Primitive("Any - not yet supported")

  override def validatedAttributes[A](
      response: List[OpenApiDoc.DocumentedResponse],
      invalidDocs: Documentation
  ): List[OpenApiDoc.DocumentedResponse] =
    response :+ OpenApiDoc.DocumentedResponse(
          status = 400,
          documentation = invalidDocs.getOrElse(""),
          content = Map(
            "application/json" -> MediaType(schema = Some(Schema.Array(Schema.simpleString, None)))
          )
        )

  /** API field schema */
  implicit override val fieldSchema: DocumentedJsonSchema =
    DocumentedJsonSchema.Primitive("Either String or FormattedField")
}
