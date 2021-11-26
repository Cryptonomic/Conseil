package tech.cryptonomic.conseil.platform.data.ethereum

import tech.cryptonomic.conseil.platform.data.ApiDataEndpoints

import sttp.tapir._

trait EthereumDataEndpointsCreator extends ApiDataEndpoints {

  private def createPath(platform: String) = "v2" / "data" / platform / query[String]("network")
  private def compQueryResponseList = (x: String) => compatibilityQuery[List[QueryResponse]](x)
  private def compQueryResponse = (x: String) => compatibilityQuery[QueryResponse](x)
  private val common =
    (x: String) => infallibleEndpoint.get.in(createPath(x)).in(header[Option[String]]("apiKey"))

  /** V2 Blocks endpoint definition */
  private[ethereum] def blocksEndpoint(platform: String) =
    common(platform)
    // .in(createPath(platform) / "transactions" / query[Option[String]](ethereumQsFilter))
      .in("blocks" / query[Option[String]]("ethereumQsFilter"))
      .out(compQueryResponseList("blocks"))

  /** V2 Blocks head endpoint definition */
  private[ethereum] def blocksHeadEndpoint(platform: String) =
    common(platform)
      .in("blocks" / "head")
      .out(compQueryResponse("blocks head"))

  /** V2 Blocks by hash endpoint definition */
  private[ethereum] def blockByHashEndpoint(platform: String) =
    common(platform)
      .in("blocks" / query[String]("hash"))
      .out(compQueryResponse("block by hash"))

  /** V2 Transactions endpoint definition */
  private[ethereum] def transactionsEndpoint(platform: String) =
    common(platform)
      .in("transactions" / query[Option[String]]("ethereumQsFilter"))
      .out(compQueryResponseList("transactions"))

  /** V2 Transaction by id endpoint definition */
  private[ethereum] def transactionByHashEndpoint(platform: String) =
    common(platform)
      .in("transactions" / query[String]("hash"))
      .out(compQueryResponse("transaction by hash"))

  /** V2 Logs endpoint definition */
  private[ethereum] def logsEndpoint(platform: String) =
    common(platform)
      .in("logs" / query[Option[String]]("ethereumQsFilter"))
      .out(compQueryResponseList("logs"))

  /** V2 Receipts endpoint definition */
  private[ethereum] def receiptsEndpoint(platform: String) =
    common(platform)
    // .in(createPath(platform) / "receipts" / query[Option[String]](ethereumQsFilter))
      .in("receipts" / query[Option[String]]("ethereumQsFilter"))
      .out(compQueryResponseList("receipts"))

  /** V2 Contracts endpoint definition */
  private[ethereum] def contractsEndpoint(platform: String) =
    common(platform)
      .in("contracts" / query[Option[String]]("ethereumQsFilter"))
      .out(compQueryResponseList("contracts"))

  /** V2 Tokens endpoint definition */
  private[ethereum] def tokensEndpoint(platform: String) =
    common(platform)
      .in("tokens" / query[Option[String]]("ethereumQsFilter"))
      .out(compQueryResponseList("tokens"))

  /** V2 Token transfers endpoint definition */
  private[ethereum] def tokenTransfersEndpoint(platform: String) =
    common(platform)
      .in("token_transfers" / query[Option[String]]("ethereumQsFilter"))
      .out(compQueryResponseList("token transfers"))

  /** V2 Tokens history endpoint definition */
  private[ethereum] def tokensHistoryEndpoint(platform: String) =
    common(platform)
      .in("tokens_history" / query[Option[String]]("ethereumQsFilter"))
      .out(compQueryResponseList("tokens history"))

  /** V2 Accounts endpoint definition */
  private[ethereum] def accountsEndpoint(platform: String) =
    common(platform)
      .in("accounts" / query[Option[String]]("ethereumQsFilter"))
      .out(compQueryResponseList("accounts"))

  /** V2 Accounts by address endpoint definition */
  private[ethereum] def accountByAddressEndpoint(platform: String) =
    common(platform)
      .in("accounts" / query[String]("address"))
      .out(compQueryResponse("account by address"))

  /** V2 Accounts history endpoint definition */
  private[ethereum] def accountsHistoryEndpoint(platform: String) =
    common(platform)
      .in("accounts_history" / query[Option[String]]("ethereumQsFilter"))
      .out(compQueryResponseList("accounts history"))

  // private def createTags(platform: String, tag: String): List[String] = List(s"$platform $tag")

}
