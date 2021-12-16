package tech.cryptonomic.conseil.platform.data.ethereum

/** Represents list of endpoints exposed for Ethereum Blockchain */
trait EthereumDataEndpoints extends EthereumDataEndpointsCreator {

  val ethEndpoints = List(
    ethereumQueryEndpoint,
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

  private val platform: String = "ethereum"

  def ethereumQueryEndpoint = queryEndpoint(platform)

  def ethereumBlocksEndpoint = blocksEndpoint(platform)

  def ethereumBlocksHeadEndpoint = blocksHeadEndpoint(platform)

  def ethereumBlockByHashEndpoint = blockByHashEndpoint(platform)

  def ethereumTransactionsEndpoint = transactionsEndpoint(platform)

  def ethereumTransactionByHashEndpoint = transactionByHashEndpoint(platform)

  def ethereumLogsEndpoint = logsEndpoint(platform)

  def ethereumReceiptsEndpoint = receiptsEndpoint(platform)

  def ethereumContractsEndpoint = contractsEndpoint(platform)

  def ethereumTokensEndpoint = tokensEndpoint(platform)

  def ethereumTokenTransfersEndpoint = tokenTransfersEndpoint(platform)

  def ethereumTokensHistoryEndpoint = tokensHistoryEndpoint(platform)

  def ethereumAccountsEndpoint = accountsEndpoint(platform)

  def ethereumAccountByAddressEndpoint = accountByAddressEndpoint(platform)

  def ethereumAccountsHistoryEndpoint = accountsHistoryEndpoint(platform)
}

/** Represents list of endpoints exposed for Quorum Blockchain (based on Ethereum) */
trait QuorumDataEndpoints extends EthereumDataEndpointsCreator {

  val quorumEndpoints = List(
    quorumQueryEndpoint,
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

  private val platform: String = "quorum"

  def quorumQueryEndpoint = queryEndpoint(platform)

  def quorumBlocksEndpoint = blocksEndpoint(platform)

  def quorumBlocksHeadEndpoint = blocksHeadEndpoint(platform)

  def quorumBlockByHashEndpoint = blockByHashEndpoint(platform)

  def quorumTransactionsEndpoint = transactionsEndpoint(platform)

  def quorumTransactionByHashEndpoint = transactionByHashEndpoint(platform)

  def quorumLogsEndpoint = logsEndpoint(platform)

  def quorumReceiptsEndpoint = receiptsEndpoint(platform)

  def quorumContractsEndpoint = contractsEndpoint(platform)

  def quorumTokensEndpoint = tokensEndpoint(platform)

  def quorumTokenTransfersEndpoint = tokenTransfersEndpoint(platform)

  def quorumAccountsEndpoint = accountsEndpoint(platform)

  def quorumAccountByAddressEndpoint = accountByAddressEndpoint(platform)

}
