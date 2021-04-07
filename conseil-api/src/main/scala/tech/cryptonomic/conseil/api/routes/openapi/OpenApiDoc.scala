package tech.cryptonomic.conseil.api.routes.openapi

import endpoints.algebra.Documentation
import endpoints.openapi
import endpoints.openapi.model.{Info, MediaType, OpenApi, Schema}
import tech.cryptonomic.conseil.api.routes.info.AppInfoEndpoint
import tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.BitcoinDataEndpoints
import tech.cryptonomic.conseil.api.routes.platform.data.ethereum.{EthereumDataEndpoints, QuorumDataEndpoints}
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.TezosDataEndpoints
import tech.cryptonomic.conseil.api.routes.platform.discovery.PlatformDiscoveryEndpoints
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Field, QueryResponse}
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataStandardJsonCodecs.{
  anyDecoder,
  anyEncoder,
  fieldDecoder,
  fieldEncoder,
  queryResponseDecoder,
  queryResponseEncoder
}

/** OpenAPI documentation object */
object OpenApiDoc
    extends TezosDataEndpoints
    with BitcoinDataEndpoints
    with EthereumDataEndpoints
    with QuorumDataEndpoints
    with PlatformDiscoveryEndpoints
    with AppInfoEndpoint
    with openapi.Endpoints
    with openapi.JsonEntitiesFromSchemas
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
    bitcoinAccountsEndpoint,
    bitcoinAccountByAddressEndpoint,
    ethereumBlocksEndpoint,
    ethereumBlocksHeadEndpoint,
    ethereumBlockByHashEndpoint,
    ethereumTransactionsEndpoint,
    ethereumTransactionByHashEndpoint,
    ethereumLogsEndpoint,
    ethereumReceiptsEndpoint,
    ethereumTokensEndpoint,
    ethereumTokenTransfersEndpoint,
    ethereumTokensHistoryEndpoint,
    ethereumContractsEndpoint,
    ethereumAccountsEndpoint,
    ethereumAccountByAddressEndpoint,
    ethereumAccountsHistoryEndpoint,
    quorumBlocksEndpoint,
    quorumBlocksHeadEndpoint,
    quorumBlockByHashEndpoint,
    quorumTransactionsEndpoint,
    quorumTransactionByHashEndpoint,
    quorumLogsEndpoint,
    quorumReceiptsEndpoint,
    quorumTokensEndpoint,
    quorumTokenTransfersEndpoint,
    quorumContractsEndpoint,
    quorumAccountsEndpoint,
    quorumAccountByAddressEndpoint,
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
    */
  override def validated[A](
      response: List[DocumentedResponse],
      invalidDocs: endpoints.algebra.Documentation
  ): List[DocumentedResponse] =
    response ++ List(
          DocumentedResponse(
            status = 400,
            documentation = invalidDocs.getOrElse(""),
            headers = DocumentedHeaders(List.empty),
            content = Map(
              "application/json" -> MediaType(schema = Some(Schema.Array(Left(Schema.simpleString), None, None)))
            )
          ),
          DocumentedResponse(
            status = 200,
            documentation = invalidDocs.getOrElse(""),
            headers = DocumentedHeaders(List.empty),
            content = Map(
              "application/json" -> MediaType(None),
              "text/csv" -> MediaType(None),
              "text/plain" -> MediaType(None)
            )
          )
        )

  override def validatedAttributes[A](
      response: List[DocumentedResponse],
      invalidDocs: Documentation
  ): List[DocumentedResponse] =
    response :+ DocumentedResponse(
          status = 400,
          documentation = invalidDocs.getOrElse(""),
          headers = DocumentedHeaders(List.empty),
          content = Map(
            "application/json" -> MediaType(schema = Some(Schema.Array(Left(Schema.simpleString), None, None)))
          )
        )

  /** Documented JSON schema for Any */
  implicit override lazy val anySchema: JsonSchema[Any] =
    new JsonSchema[Any](
      ujsonSchema = new ujsonSchemas.JsonSchema[Any] {
        override def encoder = anyEncoder
        override def decoder = anyDecoder
      },
      docs = DocumentedJsonSchema.Primitive("Any - not yet supported")
    )

  /** Documented JSON schema for query response */
  implicit override lazy val queryResponseSchema: JsonSchema[QueryResponse] =
    new JsonSchema[QueryResponse](
      ujsonSchema = new ujsonSchemas.JsonSchema[QueryResponse] {
        override def encoder = queryResponseEncoder
        override def decoder = queryResponseDecoder
      },
      docs = DocumentedJsonSchema.Primitive("Any - not yet supported")
    )

  /** Fields JSON schema implementation */
  implicit override lazy val fieldSchema: JsonSchema[Field] =
    new JsonSchema[Field](
      ujsonSchema = new ujsonSchemas.JsonSchema[Field] {

        override def encoder = fieldEncoder(formattedFieldSchema.ujsonSchema.encoder)

        override def decoder = fieldDecoder(formattedFieldSchema.ujsonSchema.decoder)

      },
      docs = DocumentedJsonSchema.Primitive("Any - not yet supported")
    )

}
