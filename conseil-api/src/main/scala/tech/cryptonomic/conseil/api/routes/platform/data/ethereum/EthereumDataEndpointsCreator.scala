package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import tech.cryptonomic.conseil.api.routes.platform.data.{ApiDataEndpoints, ApiDataJsonSchemas}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse

/** Trait containing endpoints definitions for Ethereum-related blockchains */
trait EthereumDataEndpointsCreator extends ApiDataEndpoints with ApiDataJsonSchemas with EthereumFilterFromQueryString {

  /** V2 Blocks endpoint definition */
  private[ethereum] def blocksEndpoint(platform: String): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "blocks" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("blocks"),
      tags = createTags(platform, "Blocks")
    )

  /** V2 Blocks head endpoint definition */
  private[ethereum] def blocksHeadEndpoint(
      platform: String
  ): Endpoint[(String, Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = createPath(platform) / "blocks" / "head", headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("blocks head"),
      tags = createTags(platform, "Blocks")
    )

  /** V2 Blocks by hash endpoint definition */
  private[ethereum] def blockByHashEndpoint(
      platform: String
  ): Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request =
        get(url = createPath(platform) / "blocks" / segment[String](name = "hash"), headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("block by hash"),
      tags = createTags(platform, "Blocks")
    )

  /** V2 Transactions endpoint definition */
  private[ethereum] def transactionsEndpoint(
      platform: String
  ): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "transactions" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("transactions"),
      tags = createTags(platform, "Transactions")
    )

  /** V2 Transaction by id endpoint definition */
  private[ethereum] def transactionByHashEndpoint(
      platform: String
  ): Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(
        url = createPath(platform) / "transactions" / segment[String](name = "hash"),
        headers = optHeader("apiKey")
      ),
      response = compatibilityQuery[QueryResponse]("transaction by hash"),
      tags = createTags(platform, "Transactions")
    )

  /** V2 Logs endpoint definition */
  private[ethereum] def logsEndpoint(platform: String): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "logs" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("logs"),
      tags = createTags(platform, "Logs")
    )

  /** V2 Receipts endpoint definition */
  private[ethereum] def receiptsEndpoint(platform: String): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "receipts" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("receipts"),
      tags = createTags(platform, "Receipts")
    )

  /** V2 Contracts endpoint definition */
  private[ethereum] def contractsEndpoint(
      platform: String
  ): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "contracts" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("contracts"),
      tags = createTags(platform, "Contracts")
    )

  /** V2 Tokens endpoint definition */
  private[ethereum] def tokensEndpoint(platform: String): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "tokens" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("tokens"),
      tags = createTags(platform, "Tokens")
    )

  /** V2 Token transfers endpoint definition */
  private[ethereum] def tokenTransfersEndpoint(
      platform: String
  ): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "token_transfers" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("token transfers"),
      tags = createTags(platform, "Token transfers")
    )

  /** V2 Accounts endpoint definition */
  private[ethereum] def accountsEndpoint(platform: String): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "accounts" /? ethereumQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("accounts"),
      tags = createTags(platform, "Accounts")
    )

  /** V2 Accounts by address endpoint definition */
  private[ethereum] def accountByAddressEndpoint(
      platform: String
  ): Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request =
        get(url = createPath(platform) / "accounts" / segment[String](name = "address"), headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("account by address"),
      tags = createTags(platform, "Accounts")
    )

  private def createPath(platform: String): Path[String] =
    path / "v2" / "data" / platform / segment[String](name = "network")

  private def createTags(platform: String, tag: String): List[String] = List(s"$platform $tag")

}
