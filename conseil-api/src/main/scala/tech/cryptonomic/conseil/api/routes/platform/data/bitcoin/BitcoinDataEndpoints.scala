package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import tech.cryptonomic.conseil.api.routes.platform.data.{ApiDataEndpoints, ApiDataJsonSchemas}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse

/** Trait containing endpoints definition */
trait BitcoinDataEndpoints extends ApiDataEndpoints with ApiDataJsonSchemas with BitcoinFilterFromQueryString {

  private val root = path / "v2" / "data" / "bitcoin" / segment[String](name = "network")

  /** V2 Blocks endpoint definition */
  def bitcoinBlocksEndpoint: Endpoint[((String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "blocks" /? bitcoinQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("blocks"),
      tags = List("Blocks")
    )

  /** V2 Blocks head endpoint definition */
  def bitcoinBlocksHeadEndpoint: Endpoint[(String, Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "blocks" / "head", headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("blocks head"),
      tags = List("Blocks")
    )

  /** V2 Blocks by hash endpoint definition */
  def bitcoinBlockByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "blocks" / segment[String](name = "hash"), headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("block by hash"),
      tags = List("Blocks")
    )

  /** V2 Transactions endpoint definition */
  def bitcoinTransactionsEndpoint: Endpoint[((String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "transactions" /? bitcoinQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("transactions"),
      tags = List("Transactions")
    )

  /** V2 Transaction by id endpoint definition */
  def bitcoinTransactionByIdEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "transactions" / segment[String](name = "id"), headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("transaction by id"),
      tags = List("Transactions")
    )

  /** V2 Inputs for transactions endpoint definition */
  def bitcoinInputsEndpoint: Endpoint[((String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "inputs" /? bitcoinQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("inputs for transactions"),
      tags = List("Inputs")
    )

  /** V2 Outputs for transactions endpoint definition */
  def bitcoinOutputsEndpoint: Endpoint[((String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "outputs" /? bitcoinQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("outputs for transactions"),
      tags = List("Outputs")
    )

  /** V2 Accounts endpoint definition */
  def bitcoinAccountsEndpoint: Endpoint[((String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = bitcoinPath / "accounts" /? bitcoinQsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("accounts"),
      tags = List("Accounts")
    )

  /** V2 Accounts by address endpoint definition */
  def bitcoinAccountByAddressEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = bitcoinPath / "accounts" / segment[String](name = "address"), headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("account by address"),
      tags = List("Accounts")
    )

}
