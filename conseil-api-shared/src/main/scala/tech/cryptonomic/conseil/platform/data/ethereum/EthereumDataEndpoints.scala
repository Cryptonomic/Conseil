package tech.cryptonomic.conseil.platform.data.ethereum

/** Represents list of endpoints exposed for Ethereum Blockchain */
trait EthereumDataEndpoints extends EthereumDataEndpointsCreator {

  lazy val ethEndpoints = List(
    // ethereumQueryEndpoint,
    ethereumBlocksEndpoint,
    ethereumBlocksHeadEndpoint,
    ethereumBlockByHashEndpoint,
    ethereumTransactionsEndpoint,
    ethereumTransactionByHashEndpoint,
    ethereumLogsEndpoint,
    ethereumReceiptsEndpoint,
    ethereumContractsEndpoint,
    ethereumTokensEndpoint,
    ethereumTokenTransfersEndpoint,
    ethereumTokensHistoryEndpoint,
    ethereumAccountsEndpoint,
    ethereumAccountByAddressEndpoint,
    ethereumAccountsHistoryEndpoint
  )

  val ethPlatform: String = "ethereum"

  // def ethereumQueryEndpoint = queryEndpoint(ethPlatform)

  def ethereumBlocksEndpoint = blocksEndpoint(ethPlatform)

  def ethereumBlocksHeadEndpoint = blocksHeadEndpoint(ethPlatform)

  def ethereumBlockByHashEndpoint = blockByHashEndpoint(ethPlatform)

  def ethereumTransactionsEndpoint = transactionsEndpoint(ethPlatform)

  def ethereumTransactionByHashEndpoint = transactionByHashEndpoint(ethPlatform)

  def ethereumLogsEndpoint = logsEndpoint(ethPlatform)

  def ethereumReceiptsEndpoint = receiptsEndpoint(ethPlatform)

  def ethereumContractsEndpoint = contractsEndpoint(ethPlatform)

  def ethereumTokensEndpoint = tokensEndpoint(ethPlatform)

  def ethereumTokenTransfersEndpoint = tokenTransfersEndpoint(ethPlatform)

  def ethereumTokensHistoryEndpoint = tokensHistoryEndpoint(ethPlatform)

  def ethereumAccountsEndpoint = accountsEndpoint(ethPlatform)

  def ethereumAccountByAddressEndpoint = accountByAddressEndpoint(ethPlatform)

  def ethereumAccountsHistoryEndpoint = accountsHistoryEndpoint(ethPlatform)
}

/** Represents list of endpoints exposed for Quorum Blockchain (based on Ethereum) */
trait QuorumDataEndpoints extends EthereumDataEndpointsCreator {

  lazy val quorumEndpoints = List(
    // quorumQueryEndpoint,
    quorumBlocksEndpoint,
    quorumBlocksHeadEndpoint,
    quorumBlockByHashEndpoint,
    quorumTransactionsEndpoint,
    quorumTransactionByHashEndpoint,
    quorumLogsEndpoint,
    quorumReceiptsEndpoint,
    quorumContractsEndpoint,
    quorumTokensEndpoint,
    quorumTokenTransfersEndpoint,
    quorumAccountsEndpoint,
    quorumAccountByAddressEndpoint
  )

  val quorumPlatform: String = "quorum"

  // def quorumQueryEndpoint = queryEndpoint(quorumPlatform)

  def quorumBlocksEndpoint = blocksEndpoint(quorumPlatform)

  def quorumBlocksHeadEndpoint = blocksHeadEndpoint(quorumPlatform)

  def quorumBlockByHashEndpoint = blockByHashEndpoint(quorumPlatform)

  def quorumTransactionsEndpoint = transactionsEndpoint(quorumPlatform)

  def quorumTransactionByHashEndpoint = transactionByHashEndpoint(quorumPlatform)

  def quorumLogsEndpoint = logsEndpoint(quorumPlatform)

  def quorumReceiptsEndpoint = receiptsEndpoint(quorumPlatform)

  def quorumContractsEndpoint = contractsEndpoint(quorumPlatform)

  def quorumTokensEndpoint = tokensEndpoint(quorumPlatform)

  def quorumTokenTransfersEndpoint = tokenTransfersEndpoint(quorumPlatform)

  def quorumAccountsEndpoint = accountsEndpoint(quorumPlatform)

  def quorumAccountByAddressEndpoint = accountByAddressEndpoint(quorumPlatform)

}
