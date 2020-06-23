package tech.cryptonomic.conseil.api.routes.platform.data.tezos.generic

import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataEndpoints
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.TezosDataOperations._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.Tables.BlocksRow

/** Trait containing endpoints definition */
trait TezosDataEndpoints extends ApiDataEndpoints with TezosDataJsonSchemas with TezosFilterFromQueryString {

  /** V2 Blocks endpoint definition */
  def blocksEndpoint: Endpoint[((String, String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
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
  def accountsEndpoint: Endpoint[((String, String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
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
  def operationGroupsEndpoint: Endpoint[((String, String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
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
  def avgFeesEndpoint: Endpoint[((String, String, TezosFilter), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = commonPath / "operations" / "avgFees" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("average fees"),
      tags = List("Fees")
    )

  /** V2 Operations endpoint definition */
  def operationsEndpoint: Endpoint[((String, String, TezosFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = commonPath / "operations" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("operations"),
      tags = List("Operations")
    )

}
