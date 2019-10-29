package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra
import tech.cryptonomic.conseil.generic.chain.DataTypes._
import tech.cryptonomic.conseil.tezos.ApiOperations.{AccountResult, BlockResult, Filter, OperationGroupResult}
import tech.cryptonomic.conseil.tezos.Tables
import tech.cryptonomic.conseil.tezos.Tables.BlocksRow

/** Trait containing endpoints definition */
trait DataEndpoints
    extends algebra.JsonSchemaEntities
    with DataJsonSchemas
    with ApiFilterFromQueryString
    with Validation {

  /** Common path among endpoints */
  private val commonPath = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network")

  /** V2 Query endpoint definition */
  def queryEndpoint: Endpoint[((String, String, String), ApiQuery, Option[String]), Option[
    Either[List[QueryValidationError], QueryResponseWithOutput]
  ]] =
    endpoint(
      request = post(
        url = commonPath / segment[String](name = "entity"),
        entity = jsonRequest[ApiQuery](),
        headers = optHeader("apiKey")
      ),
      response = validated(
        response = jsonResponse[QueryResponseWithOutput](docs = Some("Query endpoint")),
        invalidDocs = Some("Can't query - invalid entity!")
      ).orNotFound(Some("Not found")),
      tags = List("Query")
    )

  /** V2 Blocks endpoint definition */
  def blocksEndpoint: Endpoint[((String, String, Filter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = commonPath / "blocks" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("blocks"),
      tags = List("Blocks")
    )

  /** V2 Blocks head endpoint definition */
  def blocksHeadEndpoint: Endpoint[(String, String, Option[String]), Option[Tables.BlocksRow]] =
    endpoint(
      request = get(url = commonPath / "blocks" / "head", headers = optHeader("apiKey")),
      response = compatibilityQuery[BlocksRow]("blocks head"),
      tags = List("Blocks")
    )

  /** V2 Blocks by hash endpoint definition */
  def blockByHashEndpoint: Endpoint[((String, String, String), Option[String]), Option[BlockResult]] =
    endpoint(
      request = get(url = commonPath / "blocks" / segment[String](name = "hash"), headers = optHeader("apiKey")),
      response = compatibilityQuery[BlockResult]("block by hash"),
      tags = List("Blocks")
    )

  /** V2 Accounts endpoint definition */
  def accountsEndpoint: Endpoint[((String, String, Filter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = commonPath / "accounts" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("accounts"),
      tags = List("Accounts")
    )

  /** V2 Accounts by ID endpoint definition */
  def accountByIdEndpoint: Endpoint[((String, String, String), Option[String]), Option[AccountResult]] =
    endpoint(
      request = get(
        url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / "accounts" / segment[
                String
              ](name = "accountId"),
        headers = optHeader("apiKey")
      ),
      response = compatibilityQuery[AccountResult]("account"),
      tags = List("Accounts")
    )

  /** V2 Operation groups endpoint definition */
  def operationGroupsEndpoint: Endpoint[((String, String, Filter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = commonPath / "operation_groups" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("operation groups"),
      tags = List("Operation groups")
    )

  /** V2 Operation groups by ID endpoint definition */
  def operationGroupByIdEndpoint: Endpoint[((String, String, String), Option[String]), Option[OperationGroupResult]] =
    endpoint(
      request = get(
        url = commonPath / "operation_groups" / segment[String](name = "operationGroupId"),
        headers = optHeader("apiKey")
      ),
      response = compatibilityQuery[OperationGroupResult]("operation group"),
      tags = List("Operation groups")
    )

  /** V2 average fees endpoint definition */
  def avgFeesEndpoint: Endpoint[((String, String, Filter), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = commonPath / "operations" / "avgFees" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("average fees"),
      tags = List("Fees")
    )

  /** Common method for compatibility queries */
  private def compatibilityQuery[A: JsonResponse](endpointName: String): Response[Option[A]] =
    jsonResponse[A](docs = Some(s"Query compatibility endpoint for $endpointName")).orNotFound(Some("Not Found"))

  /** V2 Operations endpoint definition */
  def operationsEndpoint: Endpoint[((String, String, Filter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = commonPath / "operations" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("operations"),
      tags = List("Operations")
    )

}
