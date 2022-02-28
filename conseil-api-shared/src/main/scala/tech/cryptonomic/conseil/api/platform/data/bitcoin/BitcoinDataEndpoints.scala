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

  private def common =
    infallibleEndpoint.get
      // .securityIn(auth.apiKey(header[Option[String]]("apiKey")))
      .in(root)
      .errorOut(statusCode(StatusCode.NotFound))

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
    common
      .in("blocks" / bitcoinQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("blocks"))

  /** V2 Blocks head endpoint definition */
  def bitcoinBlocksHeadEndpoint =
    common
      .in("blocks" / "head")
      .out(compatibilityQuery[QueryResponse]("blocks head"))

  /** V2 Blocks by hash endpoint definition */
  def bitcoinBlockByHashEndpoint =
    common
      .in("blocks" / query[String]("hash"))
      .out(compatibilityQuery[QueryResponse]("block by hash"))

  /** V2 Transactions endpoint definition */
  def bitcoinTransactionsEndpoint =
    common
      .in("transactions" / bitcoinQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("transactions"))

  /** V2 Transaction by id endpoint definition */
  def bitcoinTransactionByIdEndpoint =
    common
      .in("transactions" / query[String]("id"))
      .out(compatibilityQuery[QueryResponse]("transaction by id"))

  /** V2 Inputs for transactions endpoint definition */
  def bitcoinInputsEndpoint =
    common
      .in("inputs" / bitcoinQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("inputs for transactions"))

  /** V2 Outputs for transactions endpoint definition */
  def bitcoinOutputsEndpoint =
    common
      .in("outputs" / bitcoinQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("outputs for transactions"))

  /** V2 Accounts endpoint definition */
  def bitcoinAccountsEndpoint =
    common
      .in("accounts" / bitcoinQsFilter)
      .out(compatibilityQuery[List[QueryResponse]]("accounts"))

  /** V2 Accounts by address endpoint definition */
  def bitcoinAccountByAddressEndpoint =
    common
      .in("accounts" / query[String]("address"))
      .out(compatibilityQuery[QueryResponse]("account by address"))

  private def createTags(entity: String): List[String] = List(s"Bitcoin $entity")

}
