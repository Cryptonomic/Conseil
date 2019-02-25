package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra
import tech.cryptonomic
import tech.cryptonomic.conseil
import tech.cryptonomic.conseil.generic.chain.DataTypes.AnyMap
import tech.cryptonomic.conseil.tezos
import tech.cryptonomic.conseil.tezos.{ApiOperations, Tables}
import tech.cryptonomic.conseil.tezos.DBTableMapping.Operation
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.tezos.Tables.{AccountsRow, BlocksRow, OperationGroupsRow}

trait TezosEndpoints extends algebra.Endpoints with DataJsonSchemas with EndpointsHelpers {

  private val commonPath = path / "tezos" / segment[String](name = "network")

  /** Blocks endpoint definition */
  def blocksEndpointV1: Endpoint[(String, ApiOperations.Filter, String), Seq[Tables.BlocksRow]] =
    endpoint(
      request = get(
        url = commonPath / "blocks" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[Seq[BlocksRow]](docs = Some("Endpoint for blocks")),
      tags = List("Blocks")
    )

  /** Blocks head endpoint definition */
  def blocksHeadEndpointV1: Endpoint[(String, String), Option[tezos.Tables.BlocksRow]] =
    endpoint(
      request = get(
        url = commonPath / "blocks" / "head",
        headers = header("apiKey")),
      response = jsonResponse[BlocksRow](docs = Some("Endpoint for blocks head")).orNotFound(Some("Not found")),
      tags = List("Blocks")
    )

  /** Blocks by hash endpoint definition */
  def blockByHashEndpointV1: Endpoint[(String, String, String), Option[AnyMap]] =
    endpoint(
      request = get(
        url = commonPath / "blocks" / segment[String](name = "hash"),
        headers = header("apiKey")),
      response = jsonResponse[AnyMap](docs = Some("Endpoint for block")).orNotFound(Some("Not found")),
      tags = List("Blocks")
    )

  /** Accounts endpoint definition */
  def accountsEndpointV1: Endpoint[(String, ApiOperations.Filter, String), Seq[conseil.tezos.Tables.AccountsRow]] =
    endpoint(
      request = get(
        url = commonPath / "accounts" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[Seq[AccountsRow]](docs = Some("Endpoint for accounts")),
      tags = List("Accounts")
    )

  /** Accounts by ID endpoint definition */
  def accountByIdEndpointV1: Endpoint[(String, String, String), Option[AnyMap]] =
    endpoint(
      request = get(
        url = commonPath / "accounts" / segment[String](name = "accountId"),
        headers = header("apiKey")),
      response = jsonResponse[AnyMap](docs = Some("Endpoint for account")).orNotFound(Some("Not found")),
      tags = List("Accounts")
    )

  /** Operation group endpoint definition */
  def operationGroupsEndpointV1: Endpoint[(String, ApiOperations.Filter, String), Seq[cryptonomic.conseil.tezos.Tables.OperationGroupsRow]] =
    endpoint(
      request = get(
        url = commonPath / "operation_groups" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[Seq[OperationGroupsRow]](docs = Some("Endpoint for operation groups")),
      tags = List("Operation groups")
    )

  /** Operation groups by ID endpoint definition */
  def operationGroupByIdEndpointV1: Endpoint[(String, String, String), Option[AnyMap]] =
    endpoint(
      request = get(
        url = commonPath / "operation_groups" / segment[String](name = "operationGroupId"),
        headers = header("apiKey")),
      response = jsonResponse[AnyMap](docs = Some("Endpoint for operation group")).orNotFound(Some("Not found")),
      tags = List("Operation groups")
    )

  /** Average fees endpoint definition */
  def avgFeesEndpointV1: Endpoint[(String, ApiOperations.Filter, String), Option[AverageFees]] =
    endpoint(
      request = get(
        url = commonPath / "operations" / "avgFees" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[AverageFees](docs = Some("Endpoint for average fees")).orNotFound(Some("Not found")),
      tags = List("Fees")
    )

  /** Operations endpoint definition */
  def operationsEndpointV1: Endpoint[(String, ApiOperations.Filter, String), Seq[Operation]] =
    endpoint(
      request = get(
        url = commonPath / "operations" /? queryStringFilter,
        headers = header("apiKey")),
      response = jsonResponse[Seq[Operation]](docs = Some("Endpoint for operations")),
      tags = List("Operations")
    )

}
