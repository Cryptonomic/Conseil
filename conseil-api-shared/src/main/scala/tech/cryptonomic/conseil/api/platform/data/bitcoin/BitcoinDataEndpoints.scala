package tech.cryptonomic.conseil.api.platform.data.bitcoin

import sttp.tapir._
import sttp.model.StatusCode
import tech.cryptonomic.conseil.api.platform.data._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.api.platform.data.ApiDataEndpoints

/** Trait containing endpoints definition */
trait BitcoinDataEndpoints extends ApiDataEndpoints with BitcoinFilterFromQueryString {

  import tech.cryptonomic.conseil.api.platform.data.converters._
  import tech.cryptonomic.conseil.api.platform.data.schemas._

  val btcPlatform = "bitcoin"

  private def root: EndpointInput[String] =
    "v2" / "data" / btcPlatform / query[String]("network")

  // lazy val btcEndpoints: List[Endpoint[Unit, _, _, _, Any]] = List(
  lazy val btcEndpoints = List(
    bitcoinQueryEndpoint,
    bitcoinBlocksEndpoint,
    bitcoinBlocksHeadEndpoint,
    bitcoinBlockByHashEndpoint,
    bitcoinTransactionsEndpoint,
    bitcoinTransactionByIdEndpoint,
    bitcoinInputsEndpoint,
    bitcoinOutputsEndpoint,
    bitcoinAccountsEndpoint,
    bitcoinAccountByAddressEndpoint
  )

  def bitcoinQueryEndpoint = queryEndpoint(btcPlatform)

  /** V2 Blocks endpoint definition */
  def bitcoinBlocksEndpoint =
    infallibleEndpoint.get
      .in(root / "blocks" / bitcoinQsFilter)
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[List[QueryResponse]]("blocks"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Blocks head endpoint definition */
  def bitcoinBlocksHeadEndpoint =
    infallibleEndpoint.get
      .in(root / "blocks" / "head")
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[QueryResponse]("blocks head"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Blocks by hash endpoint definition */
  def bitcoinBlockByHashEndpoint =
    infallibleEndpoint.get
      .in(root / "blocks" / query[String]("hash"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[QueryResponse]("block by hash"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Transactions endpoint definition */
  def bitcoinTransactionsEndpoint =
    infallibleEndpoint.get
      .in(root / "transactions" / bitcoinQsFilter)
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[List[QueryResponse]]("transactions"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Transaction by id endpoint definition */
  def bitcoinTransactionByIdEndpoint =
    infallibleEndpoint.get
      .in(root / "transactions" / query[String]("id"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[QueryResponse]("transaction by id"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Inputs for transactions endpoint definition */
  def bitcoinInputsEndpoint =
    infallibleEndpoint.get
      .in(root / "inputs" / bitcoinQsFilter)
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[List[QueryResponse]]("inputs for transactions"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Outputs for transactions endpoint definition */
  def bitcoinOutputsEndpoint =
    infallibleEndpoint.get
      .in(root / "outputs" / bitcoinQsFilter)
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[List[QueryResponse]]("outputs for transactions"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Accounts endpoint definition */
  def bitcoinAccountsEndpoint =
    infallibleEndpoint.get
      .in(root / "accounts" / bitcoinQsFilter)
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[List[QueryResponse]]("accounts"))
      .errorOut(statusCode(StatusCode.NotFound))

  /** V2 Accounts by address endpoint definition */
  def bitcoinAccountByAddressEndpoint =
    infallibleEndpoint.get
      .in(root / "accounts" / query[String]("address"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[QueryResponse]("account by address"))
      .errorOut(statusCode(StatusCode.NotFound))

  private def createTags(entity: String): List[String] = List(s"Bitcoin $entity")

}
