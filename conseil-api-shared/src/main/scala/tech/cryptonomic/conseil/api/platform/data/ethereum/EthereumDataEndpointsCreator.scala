package tech.cryptonomic.conseil.api.platform.data.ethereum

import tech.cryptonomic.conseil.common.generic.chain.DataTypes
import tech.cryptonomic.conseil.api.platform.data.ApiDataEndpoints

import sttp.tapir._
import sttp.model.StatusCode

trait EthereumDataEndpointsCreator extends ApiDataEndpoints with EthereumFilterFromQueryString {

  import tech.cryptonomic.conseil.api.platform.data.converters._
  import tech.cryptonomic.conseil.api.platform.data.schemas._

  private def createPath(platform: String): EndpointInput[String] = "v2" / "data" / platform / query[String]("network")

  private def compQueryResponse(x: String): EndpointOutput[DataTypes.QueryResponse] =
    compatibilityQuery[DataTypes.QueryResponse](x)
  private def compQueryResponseList(x: String): EndpointOutput[List[DataTypes.QueryResponse]] =
    compatibilityQuery[List[DataTypes.QueryResponse]](x)

  private def common(x: String) =
    infallibleEndpoint.get
      .in(createPath(x))
      .in(header[Option[String]]("apiKey"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Blocks endpoint definition */
  private[ethereum] def blocksEndpoint(platform: String) =
    common(platform)
      .in("blocks" / ethereumQsFilter)
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
      .in("transactions" / ethereumQsFilter)
      .out(compQueryResponseList("transactions"))

  /** V2 Transaction by id endpoint definition */
  private[ethereum] def transactionByHashEndpoint(platform: String) =
    common(platform)
      .in("transactions" / query[String]("hash"))
      .out(compQueryResponse("transaction by hash"))

  /** V2 Logs endpoint definition */
  private[ethereum] def logsEndpoint(platform: String) =
    common(platform)
      .in("logs" / ethereumQsFilter)
      .out(compQueryResponseList("logs"))

  /** V2 Receipts endpoint definition */
  private[ethereum] def receiptsEndpoint(platform: String) =
    common(platform)
      .in("receipts" / ethereumQsFilter)
      .out(compQueryResponseList("receipts"))

  /** V2 Contracts endpoint definition */
  private[ethereum] def contractsEndpoint(platform: String) =
    common(platform)
      .in("contracts" / ethereumQsFilter)
      .out(compQueryResponseList("contracts"))

  /** V2 Tokens endpoint definition */
  private[ethereum] def tokensEndpoint(platform: String) =
    common(platform)
      .in("tokens" / ethereumQsFilter)
      .out(compQueryResponseList("tokens"))

  /** V2 Token transfers endpoint definition */
  private[ethereum] def tokenTransfersEndpoint(platform: String) =
    common(platform)
      .in("token_transfers" / ethereumQsFilter)
      .out(compQueryResponseList("token transfers"))

  /** V2 Tokens history endpoint definition */
  private[ethereum] def tokensHistoryEndpoint(platform: String) =
    common(platform)
      .in("tokens_history" / ethereumQsFilter)
      .out(compQueryResponseList("tokens history"))

  /** V2 Accounts endpoint definition */
  private[ethereum] def accountsEndpoint(platform: String) =
    common(platform)
      .in("accounts" / ethereumQsFilter)
      .out(compQueryResponseList("accounts"))

  /** V2 Accounts by address endpoint definition */
  private[ethereum] def accountByAddressEndpoint(platform: String) =
    common(platform)
      .in("accounts" / query[String]("address"))
      .out(compQueryResponse("account by address"))

  /** V2 Accounts history endpoint definition */
  private[ethereum] def accountsHistoryEndpoint(platform: String) =
    common(platform)
      .in("accounts_history" / ethereumQsFilter)
      .out(compQueryResponseList("accounts history"))

  private def createTags(platform: String, tag: String): List[String] = List(s"$platform $tag")

}
