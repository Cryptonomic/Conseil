package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra
import tech.cryptonomic.conseil.generic.chain.DataTypes.{AnyMap, ApiQuery, QueryValidationError}
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.tezos.Tables
import tech.cryptonomic.conseil.tezos.Tables.BlocksRow


trait DataEndpoints
  extends algebra.Endpoints
    with JsonSchemas
    with EndpointsHelpers {

  private val commonPath = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network")

  def queryEndpoint: Endpoint[((String, String, String), ApiQuery, String), Option[Either[List[QueryValidationError], List[Map[String, Option[Any]]]]]] =
    endpoint(
      request = post(url = commonPath / segment[String](name = "entity"),
        entity = jsonRequest[ApiQuery](),
        headers = header("apiKey")),
      response = validated(
        response = jsonResponse[List[Map[String, Option[Any]]]](docs = Some("Query endpoint")),
        invalidDocs = Some("Can't query - invalid entity!")
      ).orNotFound(Some("Not found")),
      tags = List("Query")
    )

  def blocksEndpoint: Endpoint[((String, String, Filter), String), Option[List[Map[String, Option[Any]]]]] =
    endpoint(
      request = get(
        url = commonPath / "blocks" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[List[Map[String, Option[Any]]]](docs = Some("Query compatibility endpoint for blocks")).orNotFound(Some("Not found")),
      tags = List("Blocks")
    )

  def blocksHeadEndpoint: Endpoint[(String, String, String), Option[Tables.BlocksRow]] =
    endpoint(
      request = get(
        url = commonPath / "blocks" / "head",
        headers = header("apiKey")),
      response = jsonResponse[BlocksRow](docs = Some("Query compatibility endpoint for blocks head")).orNotFound(Some("Not found")),
      tags = List("Blocks")
    )

  def blockByHashEndpoint: Endpoint[((String, String, String), String), Option[AnyMap]] =
    endpoint(
      request = get(
        url = commonPath / "blocks" / segment[String](name = "hash"),
        headers = header("apiKey")),
      response = jsonResponse[AnyMap](docs = Some("Query compatibility endpoint for block")).orNotFound(Some("Not found")),
      tags = List("Blocks")
    )

  def accountsEndpoint: Endpoint[((String, String, Filter), String), Option[List[Map[String, Option[Any]]]]] =
    endpoint(
      request = get(
        url = commonPath / "accounts" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[List[Map[String, Option[Any]]]](docs = Some("Query compatibility endpoint for accounts")).orNotFound(Some("Not found")),
      tags = List("Accounts")
    )

  def accountByIdEndpoint: Endpoint[((String, String, String), String), Option[AnyMap]] =
    endpoint(
      request = get(
        url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / "accounts" / segment[String](name = "accountId"),
        headers = header("apiKey")),
      response = jsonResponse[AnyMap](docs = Some("Query compatibility endpoint for account")).orNotFound(Some("Not found")),
      tags = List("Accounts")
    )

  def operationGroupsEndpoint: Endpoint[((String, String, Filter), String), Option[List[Map[String, Option[Any]]]]] =
    endpoint(
      request = get(
        url = commonPath / "operation_groups" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[List[Map[String, Option[Any]]]](docs = Some("Query compatibility endpoint for operation groups")).orNotFound(Some("Not found")),
      tags = List("Operation groups")
    )

  def operationGroupByIdEndpoint: Endpoint[((String, String, String), String), Option[AnyMap]] =
    endpoint(
      request = get(
        url = commonPath / "operation_groups" / segment[String](name = "operationGroupId"),
        headers = header("apiKey")),
      response = jsonResponse[AnyMap](docs = Some("Query compatibility endpoint for operation group")).orNotFound(Some("Not found")),
      tags = List("Operation groups")
    )

  def avgFeesEndpoint: Endpoint[((String, String, Filter), String), Option[AverageFees]] =
    endpoint(
      request = get(
        url = commonPath / "operations" / "avgFees" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[AverageFees](docs = Some("Query compatibility endpoint for average fees")).orNotFound(Some("Not found")),
      tags = List("Fees")
    )

  def operationsEndpoint: Endpoint[((String, String, Filter), String), Option[List[Map[String, Option[Any]]]]] =
    endpoint(
      request = get(
        url = commonPath / "operations" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[List[Map[String, Option[Any]]]](docs = Some("Query compatibility endpoint for operations")).orNotFound(Some("Not found")),
      tags = List("Operations")
    )

}
