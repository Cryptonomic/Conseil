package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import tech.cryptonomic.conseil.api.routes.platform.data.{ApiDataEndpoints, ApiDataJsonSchemas}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse
import endpoints.algebra

/** Trait containing endpoints definitions for Ethereum-related blockchains */
trait EthereumDataEndpointsCreator
    extends algebra.Endpoints
    with ApiDataEndpoints
    with ApiDataJsonSchemas
    with EthereumFilterFromQueryString {

  /** V2 Blocks endpoint definition */
  private[ethereum] def blocksEndpoint(platform: String): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "blocks" /? ethereumQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("blocks"),
      docs = EndpointDocs(tags = createTags(platform, "Blocks"))
    )

  /** V2 Blocks head endpoint definition */
  private[ethereum] def blocksHeadEndpoint(
      platform: String
  ): Endpoint[(String, Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = createPath(platform) / "blocks" / "head", headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("blocks head"),
      docs = EndpointDocs(tags = createTags(platform, "Blocks"))
    )

  /** V2 Blocks by hash endpoint definition */
  private[ethereum] def blockByHashEndpoint(
      platform: String
  ): Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(
        url = createPath(platform) / "blocks" / segment[String](name = "hash"),
        headers = optRequestHeader("apiKey")
      ),
      response = compatibilityQuery[QueryResponse]("block by hash"),
      docs = EndpointDocs(tags = createTags(platform, "Blocks"))
    )

  /** V2 Transactions endpoint definition */
  private[ethereum] def transactionsEndpoint(
      platform: String
  ): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request =
        get(url = createPath(platform) / "transactions" /? ethereumQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("transactions"),
      docs = EndpointDocs(tags = createTags(platform, "Transactions"))
    )

  /** V2 Transaction by id endpoint definition */
  private[ethereum] def transactionByHashEndpoint(
      platform: String
  ): Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(
        url = createPath(platform) / "transactions" / segment[String](name = "hash"),
        headers = optRequestHeader("apiKey")
      ),
      response = compatibilityQuery[QueryResponse]("transaction by hash"),
      docs = EndpointDocs(tags = createTags(platform, "Transactions"))
    )

  /** V2 Logs endpoint definition */
  private[ethereum] def logsEndpoint(platform: String): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "logs" /? ethereumQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("logs"),
      docs = EndpointDocs(tags = createTags(platform, "Logs"))
    )

  /** V2 Receipts endpoint definition */
  private[ethereum] def receiptsEndpoint(platform: String): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "receipts" /? ethereumQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("receipts"),
      docs = EndpointDocs(tags = createTags(platform, "Receipts"))
    )

  /** V2 Contracts endpoint definition */
  private[ethereum] def contractsEndpoint(
      platform: String
  ): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "contracts" /? ethereumQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("contracts"),
      docs = EndpointDocs(tags = createTags(platform, "Contracts"))
    )

  /** V2 Tokens endpoint definition */
  private[ethereum] def tokensEndpoint(platform: String): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "tokens" /? ethereumQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("tokens"),
      docs = EndpointDocs(tags = createTags(platform, "Tokens"))
    )

  /** V2 Token transfers endpoint definition */
  private[ethereum] def tokenTransfersEndpoint(
      platform: String
  ): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request =
        get(url = createPath(platform) / "token_transfers" /? ethereumQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("token transfers"),
      docs = EndpointDocs(tags = createTags(platform, "Token transfers"))
    )

  /** V2 Tokens history endpoint definition */
  private[ethereum] def tokensHistoryEndpoint(
      platform: String
  ): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request =
        get(url = createPath(platform) / "tokens_history" /? ethereumQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("tokens history"),
      docs = EndpointDocs(tags = createTags(platform, "Tokens history"))
    )

  /** V2 Accounts endpoint definition */
  private[ethereum] def accountsEndpoint(platform: String): Endpoint[((String, EthereumFilter), Option[String]), Option[
    List[QueryResponse]
  ]] =
    endpoint(
      request = get(url = createPath(platform) / "accounts" /? ethereumQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("accounts"),
      docs = EndpointDocs(tags = createTags(platform, "Accounts"))
    )

  /** V2 Accounts by address endpoint definition */
  private[ethereum] def accountByAddressEndpoint(
      platform: String
  ): Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(
        url = createPath(platform) / "accounts" / segment[String](name = "address"),
        headers = optRequestHeader("apiKey")
      ),
      response = compatibilityQuery[QueryResponse]("account by address"),
      docs = EndpointDocs(tags = createTags(platform, "Accounts"))
    )

  private def createPath(platform: String): Path[String] =
    path / "v2" / "data" / platform / segment[String](name = "network")

  private def createTags(platform: String, tag: String): List[String] = List(s"$platform $tag")

}
