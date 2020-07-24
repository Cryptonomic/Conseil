package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse

/** Represents list of endpoints exposed for Ethereum Blockchain */
trait EthereumDataEndpoints extends EthereumDataEndpointsCreator {

  private val root = path / "v2" / "data" / "ethereum" / segment[String](name = "network")

  def ethereumBlocksEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[List[QueryResponse]]] =
    blocksEndpoint(root)

  def ethereumBlocksHeadEndpoint: Endpoint[(String, Option[String]), Option[QueryResponse]] =
    blocksHeadEndpoint(root)

  def ethereumBlockByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    blockByHashEndpoint(root)

  def ethereumTransactionsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[List[QueryResponse]]] =
    transactionsEndpoint(root)

  def ethereumTransactionByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    transactionByHashEndpoint(root)

  def ethereumLogsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    logsEndpoint(root)

}

/** Represents list of endpoints exposed for Quorum Blockchain (based on Ethereum) */
trait QuorumDataEndpoints extends EthereumDataEndpointsCreator {

  private val root = path / "v2" / "data" / "quorum" / segment[String](name = "network")

  def quorumBlocksEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[List[QueryResponse]]] =
    blocksEndpoint(root)

  def quorumBlocksHeadEndpoint: Endpoint[(String, Option[String]), Option[QueryResponse]] =
    blocksHeadEndpoint(root)

  def quorumBlockByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    blockByHashEndpoint(root)

  def quorumTransactionsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[List[QueryResponse]]] =
    transactionsEndpoint(root)

  def quorumTransactionByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    transactionByHashEndpoint(root)

  def quorumLogsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    logsEndpoint(root)

}
