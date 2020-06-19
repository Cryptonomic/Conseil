package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataEndpoints
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse

/** Trait containing endpoints definition */
trait BitcoinDataEndpoints extends ApiDataEndpoints with BitcoinDataJsonSchemas with BitcoinApiFilterFromQueryString {

  /** V2 Blocks endpoint definition */
  def blocksEndpoint: Endpoint[((String, String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = commonPath / "blocks" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("blocks"),
      tags = List("Blocks")
    )

  /** V2 Blocks head endpoint definition */
  def blocksHeadEndpoint: Endpoint[(String, String, Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = commonPath / "blocks" / "head", headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("blocks head"),
      tags = List("Blocks")
    )

  /** V2 Blocks by hash endpoint definition */
  def blockByHashEndpoint: Endpoint[((String, String, String), Option[String]), Option[QueryResponse]] =
    endpoint(
      request = get(url = commonPath / "blocks" / segment[String](name = "hash"), headers = optHeader("apiKey")),
      response = compatibilityQuery[QueryResponse]("block by hash"),
      tags = List("Blocks")
    )

  /** V2 Transactions endpoint definition */
  def transactionsEndpoint: Endpoint[((String, String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = commonPath / "transactions" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("transactions"),
      tags = List("Transactions")
    )

  /** V2 Inputs for transactions endpoint definition */
  def inputsEndpoint: Endpoint[((String, String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = commonPath / "inputs" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("inputs for transactions"),
      tags = List("Inputs")
    )

  /** V2 Outputs for transactions endpoint definition */
  def outputsEndpoint: Endpoint[((String, String, BitcoinFilter), Option[String]), Option[List[QueryResponse]]] =
    endpoint(
      request = get(url = commonPath / "outputs" /? qsFilter, headers = optHeader("apiKey")),
      response = compatibilityQuery[List[QueryResponse]]("outputs for transactions"),
      tags = List("Outputs")
    )

}
