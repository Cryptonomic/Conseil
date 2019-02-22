package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra
import tech.cryptonomic.conseil.generic.chain.DataTypes.{AnyMap, QueryResponse}
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.tezos.Tables.{AccountsRow, BlocksRow}

trait TezosEndpoints extends algebra.Endpoints
  with DataJsonSchemas
  with EndpointsHelpers {

  val commonPath = path / "tezos" / segment[String](name = "network")

  /** V2 Blocks endpoint definition */
  def blocksEndpoint =
    endpoint(
      request = get(
        url = commonPath / "blocks" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[Seq[BlocksRow]](docs = Some("Query compatibility endpoint for blocks")),
      tags = List("Blocks")
    )

  /** V2 Blocks head endpoint definition */
  def blocksHeadEndpoint =
    endpoint(
      request = get(
        url = commonPath / "blocks" / "head",
        headers = header("apiKey")),
      response = jsonResponse[BlocksRow](docs = Some("Query compatibility endpoint for blocks head")).orNotFound(Some("Not found")),
      tags = List("Blocks")
    )

  /** V2 Blocks by hash endpoint definition */
  def blockByHashEndpoint =
    endpoint(
      request = get(
        url = commonPath / "blocks" / segment[String](name = "hash"),
        headers = header("apiKey")),
      response = jsonResponse[AnyMap](docs = Some("Query compatibility endpoint for block")).orNotFound(Some("Not found")),
      tags = List("Blocks")
    )

  /** V2 Accounts endpoint definition */
  def accountsEndpoint=
    endpoint(
      request = get(
        url = commonPath / "accounts" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[Seq[AccountsRow]](docs = Some("Query compatibility endpoint for accounts")),
      tags = List("Accounts")
    )

  /** V2 Accounts by ID endpoint definition */
  def accountByIdEndpoint =
    endpoint(
      request = get(
        url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / "accounts" / segment[String](name = "accountId"),
        headers = header("apiKey")),
      response = jsonResponse[AnyMap](docs = Some("Query compatibility endpoint for account")).orNotFound(Some("Not found")),
      tags = List("Accounts")
    )

  /** V2 Operation groupe endpoint definition */
  def operationGroupsEndpoint =
    endpoint(
      request = get(
        url = commonPath / "operation_groups" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[List[QueryResponse]](docs = Some("Query compatibility endpoint for operation groups")).orNotFound(Some("Not found")),
      tags = List("Operation groups")
    )

  /** V2 Operation groups by ID endpoint definition */
  def operationGroupByIdEndpoint =
    endpoint(
      request = get(
        url = commonPath / "operation_groups" / segment[String](name = "operationGroupId"),
        headers = header("apiKey")),
      response = jsonResponse[AnyMap](docs = Some("Query compatibility endpoint for operation group")).orNotFound(Some("Not found")),
      tags = List("Operation groups")
    )

  /** V2 average fees endpoint definition */
  def avgFeesEndpoint =
    endpoint(
      request = get(
        url = commonPath / "operations" / "avgFees" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[AverageFees](docs = Some("Query compatibility endpoint for average fees")).orNotFound(Some("Not found")),
      tags = List("Fees")
    )

  /** V2 Operations endpoint definition */
  def operationsEndpoint =
    endpoint(
      request = get(
        url = commonPath / "operations" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[List[QueryResponse]](docs = Some("Query compatibility endpoint for operations")).orNotFound(Some("Not found")),
      tags = List("Operations")
    )


}
