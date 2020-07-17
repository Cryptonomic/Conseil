package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import tech.cryptonomic.conseil.api.routes.platform.data.{ApiDataEndpoints, ApiDataJsonSchemas}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse

/** Trait containing endpoints definition */
trait EthereumDataEndpoints extends ApiDataEndpoints with ApiDataJsonSchemas with EthereumFilterFromQueryString {

  private val ethereumPath = path / "v2" / "data" / "ethereum" / segment[String](name = "network")

  /** V2 Blocks endpoint definition */
  def ethereumBlocksEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = ethereumPath / "blocks" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("blocks"),
      tags = List("Blocks")
    )

  /** V2 Blocks head endpoint definition */
  def ethereumBlocksHeadEndpoint: Endpoint[(String, Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = ethereumPath / "blocks" / "head", headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("blocks head"),
      tags = List("Blocks")
    )

  /** V2 Blocks by hash endpoint definition */
  def ethereumBlockByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = ethereumPath / "blocks" / segment[String](name = "hash"), headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("block by hash"),
      tags = List("Blocks")
    )

  /** V2 Transactions endpoint definition */
  def ethereumTransactionsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = ethereumPath / "transactions" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("transactions"),
      tags = List("Transactions")
    )

  /** V2 Transaction by id endpoint definition */
  def ethereumTransactionByIdEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = ethereumPath / "transactions" / segment[String](name = "id"), headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("transaction by id"),
      tags = List("Transactions")
    )

}
