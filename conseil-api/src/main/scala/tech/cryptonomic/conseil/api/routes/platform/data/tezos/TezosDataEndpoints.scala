package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import tech.cryptonomic.conseil.api.routes.platform.data.{ApiDataEndpoints, ApiDataTypes}
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.TezosDataOperations._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.Tables.BlocksRow
import endpoints.algebra
import tech.cryptonomic.conseil.api.routes.validation.Validation.QueryValidating

/** Trait containing endpoints definition */
trait TezosDataEndpoints
    extends algebra.Endpoints
    with ApiDataEndpoints
    with TezosDataJsonSchemas
    with TezosFilterFromQueryString {

  private val platform = "tezos"

  private val root = path / "v2" / "data" / platform / segment[String](name = "network")

  def tezosQueryEndpoint: Endpoint[(String, String, ApiDataTypes.ApiQuery, Option[String]), Option[
    QueryValidating[QueryResponseWithOutput]
  ]] = queryEndpoint(platform)

  /** V2 Blocks endpoint definition */
  def tezosBlocksEndpoint: Endpoint[((String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "blocks" /? tezosQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("blocks"),
      docs = EndpointDocs(tags = createTags("Blocks"))
    )

  /** V2 Blocks head endpoint definition */
  def tezosBlocksHeadEndpoint: Endpoint[(String, Option[String]), Option[Tables.BlocksRow]] =
    endpoint(
      request = get(url = root / "blocks" / "head", headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[BlocksRow]("blocks head"),
      docs = EndpointDocs(tags = createTags("Blocks"))
    )

  /** V2 Blocks by hash endpoint definition */
  def tezosBlockByHashEndpoint: Endpoint[((String, String), Option[String]), Option[BlockResult]] =
    endpoint(
      request = get(url = root / "blocks" / segment[String](name = "hash"), headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[BlockResult]("block by hash"),
      docs = EndpointDocs(tags = createTags("Blocks"))
    )

  /** V2 Accounts endpoint definition */
  def tezosAccountsEndpoint: Endpoint[((String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "accounts" /? tezosQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("accounts"),
      docs = EndpointDocs(tags = createTags("Accounts"))
    )

  /** V2 Accounts by ID endpoint definition */
  def tezosAccountByIdEndpoint: Endpoint[((String, String), Option[String]), Option[AccountResult]] =
    endpoint(
      request = get(
        url = root / "accounts" / segment[String](name = "accountId"),
        headers = optRequestHeader("apiKey")
      ),
      response = compatibilityQuery[AccountResult]("account"),
      docs = EndpointDocs(tags = createTags("Accounts"))
    )

  /** V2 Operation groups endpoint definition */
  def tezosOperationGroupsEndpoint: Endpoint[((String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "operation_groups" /? tezosQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("operation groups"),
      docs = EndpointDocs(tags = createTags("Operation groups"))
    )

  /** V2 Operation groups by ID endpoint definition */
  def tezosOperationGroupByIdEndpoint: Endpoint[((String, String), Option[String]), Option[OperationGroupResult]] =
    endpoint(
      request = get(
        url = root / "operation_groups" / segment[String](name = "operationGroupId"),
        headers = optRequestHeader("apiKey")
      ),
      response = compatibilityQuery[OperationGroupResult]("operation group"),
      docs = EndpointDocs(tags = createTags("Operation groups"))
    )

  /** V2 average fees endpoint definition */
  def tezosAvgFeesEndpoint: Endpoint[((String, TezosFilter), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "operations" / "avgFees" /? tezosQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("average fees"),
      docs = EndpointDocs(tags = createTags("Fees"))
    )

  /** V2 Operations endpoint definition */
  def tezosOperationsEndpoint: Endpoint[((String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "operations" /? tezosQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("operations"),
      docs = EndpointDocs(tags = createTags("Operations"))
    )

  private def createTags(entity: String): List[String] = List(s"Tezos $entity")

}
