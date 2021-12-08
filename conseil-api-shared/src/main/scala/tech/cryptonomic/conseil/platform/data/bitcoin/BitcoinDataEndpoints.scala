package tech.cryptonomic.conseil.platform.data.bitcoin

import sttp.tapir._
import tech.cryptonomic.conseil.platform.data._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.platform.data.ApiDataEndpoints

/** Trait containing endpoints definition */
trait BitcoinDataEndpoints extends ApiDataEndpoints { // with BitcoinFilterFromQueryString {

  import tech.cryptonomic.conseil.platform.data.schemas._

  private val platform = "bitcoin"

  private val root = "v2" / "data" / platform / query[String]("network")

  def bitcoinQueryEndpoint = queryEndpoint(platform)

  /** V2 Blocks endpoint definition */
  def bitcoinBlocksEndpoint =
    infallibleEndpoint.get
      .in(root / "blocks" / query[Option[String]]("bitcoinQsFilter"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[List[QueryResponse]]("blocks"))

  /** V2 Blocks head endpoint definition */
  def bitcoinBlocksHeadEndpoint =
    infallibleEndpoint.get
      .in(root / "blocks" / "head")
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[QueryResponse]("blocks head"))

  /** V2 Blocks by hash endpoint definition */
  def bitcoinBlockByHashEndpoint =
    infallibleEndpoint.get
      .in(root / "blocks" / query[String]("hash"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[QueryResponse]("block by hash"))

  /** V2 Transactions endpoint definition */
  def bitcoinTransactionsEndpoint =
    infallibleEndpoint.get
      .in(root / "transactions" / query[Option[String]]("bitcoinQsFilter"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[List[QueryResponse]]("transactions"))

  /** V2 Transaction by id endpoint definition */
  def bitcoinTransactionByIdEndpoint =
    infallibleEndpoint.get
      .in(root / "transactions" / query[String]("id"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[QueryResponse]("transaction by id"))

  /** V2 Inputs for transactions endpoint definition */
  def bitcoinInputsEndpoint =
    infallibleEndpoint.get
      .in(root / "inputs" / query[Option[String]]("bitcoinQsFilter"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[List[QueryResponse]]("inputs for transactions"))

  /** V2 Outputs for transactions endpoint definition */
  def bitcoinOutputsEndpoint =
    infallibleEndpoint.get
      .in(root / "outputs" / query[Option[String]]("bitcoinQsFilter"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[List[QueryResponse]]("outputs for transactions"))

  /** V2 Accounts endpoint definition */
  def bitcoinAccountsEndpoint =
    infallibleEndpoint.get
      .in(root / "accounts" / query[Option[String]]("bitcoinQsFilter"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[List[QueryResponse]]("accounts"))

  /** V2 Accounts by address endpoint definition */
  def bitcoinAccountByAddressEndpoint =
    infallibleEndpoint.get
      .in(root / "accounts" / query[String]("address"))
      .in(header[Option[String]]("apiKey"))
      .out(compatibilityQuery[QueryResponse]("account by address"))

  private def createTags(entity: String): List[String] = List(s"Bitcoin $entity")

}
