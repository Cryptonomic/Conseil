package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataEndpoints
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.TezosDataOperations._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.Tables.BlocksRow

/** Trait containing endpoints definition */
trait TezosDataEndpoints extends ApiDataEndpoints with TezosDataJsonSchemas with TezosFilterFromQueryString {

  private val tezosPath = path / "v2" / "data" / "tezos" / segment[String](name = "network")

  /** V2 Blocks endpoint definition */
  def tezosBlocksEndpoint: Endpoint[((String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = tezosPath / "blocks" /? tezosQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("blocks"),
      tags = List("Blocks")
    )

  /** V2 Blocks head endpoint definition */
  def tezosBlocksHeadEndpoint: Endpoint[(String, Option[String]), Option[Tables.BlocksRow]] =
    endpoint(
      request = get(url = tezosPath / "blocks" / "head", headers = optHeader("apiKey")),
      response = compatibilityQuery[BlocksRow]("blocks head"),
      tags = List("Blocks")
    )

  /** V2 Blocks by hash endpoint definition */
  def tezosBlockByHashEndpoint: Endpoint[((String, String), Option[String]), Option[BlockResult]] =
    endpoint(
      request = get(url = tezosPath / "blocks" / segment[String](name = "hash"), headers = optHeader("apiKey")),
      response = compatibilityQuery[BlockResult]("block by hash"),
      tags = List("Blocks")
    )

  /** V2 Accounts endpoint definition */
  def tezosAccountsEndpoint: Endpoint[((String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = tezosPath / "accounts" /? tezosQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("accounts"),
      tags = List("Accounts")
    )

  /** V2 Accounts by ID endpoint definition */
  def tezosAccountByIdEndpoint: Endpoint[((String, String), Option[String]), Option[AccountResult]] =
    endpoint(
      request = get(
        url = tezosPath / "accounts" / segment[String](name = "accountId"),
        headers = optHeader("apiKey")
      ),
      response = compatibilityQuery[AccountResult]("account"),
      tags = List("Accounts")
    )

  /** V2 Operation groups endpoint definition */
  def tezosOperationGroupsEndpoint: Endpoint[((String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = tezosPath / "operation_groups" /? tezosQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("operation groups"),
      tags = List("Operation groups")
    )

  /** V2 Operation groups by ID endpoint definition */
  def tezosOperationGroupByIdEndpoint: Endpoint[((String, String), Option[String]), Option[OperationGroupResult]] =
    endpoint(
      request = get(
        url = tezosPath / "operation_groups" / segment[String](name = "operationGroupId"),
        headers = optHeader("apiKey")
      ),
      response = compatibilityQuery[OperationGroupResult]("operation group"),
      tags = List("Operation groups")
    )

  /** V2 average fees endpoint definition */
  def tezosAvgFeesEndpoint: Endpoint[((String, TezosFilter), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = tezosPath / "operations" / "avgFees" /? tezosQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("average fees"),
      tags = List("Fees")
    )

  /** V2 Operations endpoint definition */
  def tezosOperationsEndpoint: Endpoint[((String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = tezosPath / "operations" /? tezosQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("operations"),
      tags = List("Operations")
    )

}
