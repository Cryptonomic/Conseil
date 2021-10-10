package tech.cryptonomic.conseil.client.platform.data.ethereum

import tech.cryptonomic.conseil.api.routes.platform.data.ethereum._

import endpoints4s.xhr.JsonEntitiesFromSchemas
import endpoints4s.xhr.thenable._

object EthereumDataClient
    extends EthereumDataEndpoints
    with EthereumDataEndpointsCreator
    with QuorumDataEndpoints
    with Endpoints
    with JsonEntitiesFromSchemas {

  val eventuallyEthereumQuery = ethereumQueryEndpoint(String.empty, String.empty, ApiQuery, Some(String.empty))
  val eventuallyEthereumBlocks = ethereumBlocksEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyEthereumBlocksHead = ethereumBlocksHeadEndpoint(String.empty, Some(String.empty))
  val eventuallyEthereumBlockByHash = ethereumBlockByHashEndpoint((String.empty, String.empty) -> Some(String.empty))
  val eventuallyEthereumTransactions =
    ethereumTransactionsEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyEthereumTransactionByHash =
    ethereumTransactionByHashEndpoint((String.empty, String.empty) -> Some(String.empty))
  val eventuallyEthereumLogs = ethereumLogsEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyEthereumReceipts = ethereumReceiptsEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyEthereumContracts = ethereumContractsEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyEthereumTokens = ethereumTokensEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyEthereumTokenTransfers =
    ethereumTokenTransfersEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyEthereumTokensHistory =
    ethereumTokensHistoryEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyEthereumAccounts =
    ethereumAccountsEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyEthereumAccountByAddress =
    ethereumAccountByAddressEndpoint((String.empty, String.empty) -> Some(String.empty))
  val eventuallyEthereumAccountsHistory =
    ethereumAccountsHistoryEndpoint((String.empty, EthereumFilter) -> Some(String.empty))

  val eventuallyQuorumQuery = quorumQueryEndpoint((String.empty, String.empty, ApiQuery, Some(String.empty)))
  val eventuallyQuorumBlocks = quorumBlocksEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyQuorumBlocksHead = quorumBlocksHeadEndpoint(String.empty -> Some(String.empty))
  val eventuallyQuorumBlockByHash = quorumBlockByHashEndpoint((String.empty, String.empty) -> Some(String.empty))
  val eventuallyQuorumTransactions = quorumTransactionsEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyQuorumTransactionByHash =
    quorumTransactionByHashEndpoint((String.empty, String.empty) -> Some(String.empty))
  val eventuallyQuorumLogs = quorumLogsEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyQuorumReceipts = quorumReceiptsEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyQuorumContracts = quorumContractsEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyQuorumTokens = quorumTokensEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyQuorumTokenTransfers =
    quorumTokenTransfersEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyQuorumAccounts = quorumAccountsEndpoint((String.empty, EthereumFilter) -> Some(String.empty))
  val eventuallyQuorumAccountByAddress =
    quorumAccountByAddressEndpoint((String.empty, String.empty) -> Some(String.empty))
}
