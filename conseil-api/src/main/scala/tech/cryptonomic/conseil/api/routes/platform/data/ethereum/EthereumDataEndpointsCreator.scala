package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import tech.cryptonomic.conseil.api.routes.platform.data.{ApiDataEndpoints, ApiDataJsonSchemas}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse

/** Trait containing endpoints definitions for Ethereum-related blockchains */
trait EthereumDataEndpointsCreator extends ApiDataEndpoints with ApiDataJsonSchemas with EthereumFilterFromQueryString {

  /** V2 Blocks endpoint definition */
  private[ethereum] def blocksEndpoint(root: Path[String]) : Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = root / "blocks" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("blocks"),
      tags = List("Blocks")
    )

  /** V2 Blocks head endpoint definition */
  private[ethereum] def blocksHeadEndpoint(root: Path[String]): Endpoint[(String, Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "blocks" / "head", headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("blocks head"),
      tags = List("Blocks")
    )

  /** V2 Blocks by hash endpoint definition */
  private[ethereum] def blockByHashEndpoint(root: Path[String]): Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "blocks" / segment[String](name = "hash"), headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("block by hash"),
      tags = List("Blocks")
    )

  /** V2 Transactions endpoint definition */
  private[ethereum] def transactionsEndpoint(root: Path[String]): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = root / "transactions" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("transactions"),
      tags = List("Transactions")
    )

  /** V2 Transaction by id endpoint definition */
  private[ethereum] def transactionByHashEndpoint(root: Path[String]): Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "transactions" / segment[String](name = "hash"), headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("transaction by hash"),
      tags = List("Transactions")
    )

  /** V2 Logs endpoint definition */
  private[ethereum] def logsEndpoint(root: Path[String]): Endpoint[((String, EthereumFilter), Option[String]), Option[
    QueryResponse
  ]] =
    endpoint(
      request = get(url = root / "logs" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("logs"),
      tags = List("Logs")
    )

}
