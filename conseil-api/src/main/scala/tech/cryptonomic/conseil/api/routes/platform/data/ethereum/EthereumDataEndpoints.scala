package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse

/** Represents list of endpoints exposed for Ethereum Blockchain */
trait EthereumDataEndpoints extends EthereumDataEndpointsCreator {

  private val platform: String = "ethereum"

  def ethereumBlocksEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[List[QueryResponse]]] =
    blocksEndpoint(platform)

  def ethereumBlocksHeadEndpoint: Endpoint[(String, Option[String]), Option[QueryResponse]] =
    blocksHeadEndpoint(platform)

  def ethereumBlockByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    blockByHashEndpoint(platform)

  def ethereumTransactionsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[List[QueryResponse]]] =
    transactionsEndpoint(platform)

  def ethereumTransactionByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    transactionByHashEndpoint(platform)

  def ethereumLogsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    logsEndpoint(platform)

  def ethereumReciptsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    reciptsEndpoint(platform)

  def ethereumContractsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    contractsEndpoint(platform)

  def ethereumTokensEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    tokensEndpoint(platform)

  def ethereumTokenTransfersEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    tokenTransfersEndpoint(platform)

  def ethereumAccountsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    accountsEndpoint(platform)
  
  def ethereumAccountByAddressEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    accountByAddressEndpoint(platform)

}

/** Represents list of endpoints exposed for Quorum Blockchain (based on Ethereum) */
trait QuorumDataEndpoints extends EthereumDataEndpointsCreator {

  private val platform: String = "quorum"

  def quorumBlocksEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[List[QueryResponse]]] =
    blocksEndpoint(platform)

  def quorumBlocksHeadEndpoint: Endpoint[(String, Option[String]), Option[QueryResponse]] =
    blocksHeadEndpoint(platform)

  def quorumBlockByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    blockByHashEndpoint(platform)

  def quorumTransactionsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[List[QueryResponse]]] =
    transactionsEndpoint(platform)

  def quorumTransactionByHashEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    transactionByHashEndpoint(platform)

  def quorumLogsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    logsEndpoint(platform)

  def quorumReciptsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    reciptsEndpoint(platform)

  def quorumContractsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    contractsEndpoint(platform)

  def quorumTokensEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    tokensEndpoint(platform)

  def quorumTokenTransfersEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    tokenTransfersEndpoint(platform)

  def quorumAccountsEndpoint: Endpoint[((String, EthereumFilter), Option[String]), Option[QueryResponse]] =
    accountsEndpoint(platform)
  
  def quorumAccountByAddressEndpoint: Endpoint[((String, String), Option[String]), Option[QueryResponse]] =
    accountByAddressEndpoint(platform)

}
