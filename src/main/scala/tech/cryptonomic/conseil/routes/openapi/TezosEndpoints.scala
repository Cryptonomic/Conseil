package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra
import tech.cryptonomic.conseil.generic.chain.DataTypes.AnyMap
import tech.cryptonomic.conseil.tezos.ApiOperations
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.tezos.Tables.{AccountsRow, BlocksRow, OperationGroupsRow, OperationsRow}

trait TezosEndpoints extends algebra.Endpoints with DataJsonSchemas with ApiFilterFromQueryString with algebra.JsonSchemaEntities {

  private val commonPath = path / "tezos" / segment[String](name = "network")

  /** Blocks endpoint definition */
  def blocksEndpointV1: Endpoint[(String, ApiOperations.Filter, String), Seq[BlocksRow]] =
    endpoint(
      request = get(
        url = commonPath / "blocks" /? qsFilter,
        headers = header("apiKey")),
      response = jsonResponse[Seq[BlocksRow]](docs = Some("Endpoint for blocks")),
      tags = List("Blocks")
    )

  /** Blocks head endpoint definition */
  def blocksHeadEndpointV1: Endpoint[(String, String), Option[BlocksRow]] =
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
  def accountsEndpointV1: Endpoint[(String, ApiOperations.Filter, String), Seq[AccountsRow]] =
    endpoint(
      request = get(
        url = commonPath / "accounts" /? qsFilter,
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
  def operationGroupsEndpointV1: Endpoint[(String, ApiOperations.Filter, String), Seq[OperationGroupsRow]] =
    endpoint(
      request = get(
        url = commonPath / "operation_groups" /? qsFilter,
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

  /** Operations endpoint definition */
  def operationsEndpointV1: Endpoint[(String, ApiOperations.Filter, String), Seq[OperationsRow]] =
    endpoint(
      request = get(
        url = commonPath / "operations" /? qsFilter,
        headers = header("apiKey")),
      response = jsonResponse[Seq[OperationsRow]](docs = Some("Endpoint for operations")),
      tags = List("Operations")
    )

  /** Average fees endpoint definition */
  def avgFeesEndpointV1: Endpoint[(String, ApiOperations.Filter, String), Option[AverageFees]] =
    endpoint(
      request = get(
        url = commonPath / "operations" / "avgFees" /? qsFilter,
        headers = header("apiKey")),
      response = jsonResponse[AverageFees](docs = Some("Endpoint for average fees")).orNotFound(Some("Not found")),
      tags = List("Fees")
    )
}
