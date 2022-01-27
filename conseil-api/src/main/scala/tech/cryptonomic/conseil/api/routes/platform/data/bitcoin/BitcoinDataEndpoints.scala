package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import tech.cryptonomic.conseil.api.routes.platform.data.{ApiDataEndpoints, ApiDataJsonSchemas, ApiDataTypes}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse
import endpoints4s.algebra
import tech.cryptonomic.conseil.api.routes.validation.Validation.QueryValidating
import tech.cryptonomic.conseil.common.generic.chain.DataTypes

/** Trait containing endpoints definition */
trait BitcoinDataEndpoints
    extends algebra.Endpoints
    with ApiDataEndpoints
    with ApiDataJsonSchemas
    with BitcoinFilterFromQueryString {

  private val platform = "bitcoin"

  private val root = path / "v2" / "data" / platform / segment[String](name = "network")

  def bitcoinQueryEndpoint: Endpoint[(String, String, ApiDataTypes.ApiQuery, Option[String]), Option[
    QueryValidating[DataTypes.QueryResponseWithOutput]
  ]] = queryEndpoint(platform)

  /** V2 Blocks endpoint definition */
  def bitcoinBlocksEndpoint: Endpoint[((String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "blocks" /? bitcoinQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("blocks"),
      docs = EndpointDocs(tags = createTags("Blocks"))
    )

  /** V2 Blocks head endpoint definition */
  def bitcoinBlocksHeadEndpoint: Endpoint[(String, Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "blocks" / "head", headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("blocks head"),
      docs = EndpointDocs(tags = createTags("Blocks"))
    )

  /** V2 Blocks by hash endpoint definition */
  def bitcoinBlockByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "blocks" / segment[String](name = "hash"), headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("block by hash"),
      docs = EndpointDocs(tags = createTags("Blocks"))
    )

  /** V2 Transactions endpoint definition */
  def bitcoinTransactionsEndpoint: Endpoint[((String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "transactions" /? bitcoinQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("transactions"),
      docs = EndpointDocs(tags = createTags("Transactions"))
    )

  /** V2 Transaction by id endpoint definition */
  def bitcoinTransactionByIdEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "transactions" / segment[String](name = "id"), headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("transaction by id"),
      docs = EndpointDocs(tags = createTags("Transactions"))
    )

  /** V2 Inputs for transactions endpoint definition */
  def bitcoinInputsEndpoint: Endpoint[((String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "inputs" /? bitcoinQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("inputs for transactions"),
      docs = EndpointDocs(tags = createTags("Inputs"))
    )

  /** V2 Outputs for transactions endpoint definition */
  def bitcoinOutputsEndpoint: Endpoint[((String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "outputs" /? bitcoinQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("outputs for transactions"),
      docs = EndpointDocs(tags = createTags("Outputs"))
    )

  /** V2 Accounts endpoint definition */
  def bitcoinAccountsEndpoint: Endpoint[((String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = root / "accounts" /? bitcoinQsFilter, headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("accounts"),
      docs = EndpointDocs(tags = createTags("Accounts"))
    )

  /** V2 Accounts by address endpoint definition */
  def bitcoinAccountByAddressEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = root / "accounts" / segment[String](name = "address"), headers = optRequestHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("account by address"),
      docs = EndpointDocs(tags = createTags("Accounts"))
    )

  private def createTags(entity: String): List[String] = List(s"Bitcoin $entity")

}
