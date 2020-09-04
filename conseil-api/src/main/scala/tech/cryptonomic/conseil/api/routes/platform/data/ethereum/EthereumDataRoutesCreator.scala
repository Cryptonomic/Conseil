package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import cats.instances.either._
import cats.instances.future._
import cats.syntax.bitraverse._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.ethereum.EthereumTypes.EthereumBlockHash
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponseWithOutput
import tech.cryptonomic.conseil.common.metadata
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}

import scala.concurrent.{ExecutionContext, Future}

/** Trait, which contains routes for Ethereum-related block-chains */
trait EthereumDataRoutesCreator
    extends EthereumDataHelpers
    with EthereumDataEndpoints
    with ApiDataRoutes
    with LazyLogging {

  implicit def executionContext: ExecutionContext

  /** Metadata service used to validate incoming queries */
  def metadataService: MetadataService

  /** Metadata configuration used to validate visibility */
  def metadataConfiguration: MetadataConfiguration

  /** Data operations used for fetching the data from database */
  def operations: EthereumDataOperations

  /** Maximum number of results returned from API */
  def maxQueryResultSize: Int

  /** Path for the specific platform */
  def platform: PlatformPath

  /** V2 Route implementation for query endpoint */
  override val postRoute: Route = queryEndpoint.implementedByAsync {
    case ((platform, network, entity), apiQuery, _) =>
      val path = EntityPath(entity, NetworkPath(network, PlatformPath(platform)))
      pathValidation(path) {
        apiQuery
          .validate(path, metadataService, metadataConfiguration)
          .flatMap { validationResult =>
            validationResult.map { validQuery =>
              operations.queryWithPredicates(platform, entity, validQuery.withLimitCap(maxQueryResultSize)).map {
                queryResponses =>
                  QueryResponseWithOutput(queryResponses, validQuery.output)
              }
            }.left.map(Future.successful).bisequence
          }
          .map(Some(_))
      }
  }

  /** V2 Route implementation for blocks endpoint */
  private val blocksRoute: Route = ethereumBlocksEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchBlocks(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for blocks head endpoint */
  private val blocksHeadRoute: Route = ethereumBlocksHeadEndpoint.implementedByAsync {
    case (network, _) =>
      platformNetworkValidation(network) {
        operations.fetchBlocksHead()
      }
  }

  /** V2 Route implementation for blocks by hash endpoint */
  private val blockByHashRoute: Route = ethereumBlockByHashEndpoint.implementedByAsync {
    case ((network, hash), _) =>
      platformNetworkValidation(network) {
        operations.fetchBlockByHash(EthereumBlockHash(hash))
      }
  }

  /** V2 Route implementation for transactions endpoint */
  private val transactionsRoute: Route = ethereumTransactionsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchTransactions(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for transaction by id endpoint */
  private val transactionByIdRoute: Route = ethereumTransactionByHashEndpoint.implementedByAsync {
    case ((network, id), _) =>
      platformNetworkValidation(network) {
        operations.fetchTransactionByHash(id)
      }
  }

  /** V2 Route implementation for logs endpoint */
  private val logsRoute: Route = ethereumLogsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchLogs(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for receipts endpoint */
  private val receiptsRoute: Route = ethereumReceiptsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchReceipts(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for contracts endpoint */
  private val contractsRoute: Route = ethereumContractsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchContracts(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for tokens endpoint */
  private val tokensRoute: Route = ethereumTokensEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchTokens(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for token transfers endpoint */
  private val tokenTransfersRoute: Route = ethereumTokenTransfersEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchTokenTransfers(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for accounts endpoint */
  private val accountsRoute: Route = ethereumAccountsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchAccounts(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for accounts by address endpoint */
  private val accountsByAddressRoute: Route = ethereumAccountByAddressEndpoint.implementedByAsync {
    case ((network, address), _) =>
      platformNetworkValidation(network) {
        operations.fetchAccountByAddress(address)
      }
  }

  /** V2 concatenated routes */
  override val getRoute: Route =
    concat(
      blocksHeadRoute,
      blockByHashRoute,
      blocksRoute,
      transactionsRoute,
      transactionByIdRoute,
      logsRoute,
      receiptsRoute,
      contractsRoute,
      tokensRoute,
      tokenTransfersRoute,
      accountsRoute,
      accountsByAddressRoute
    )

  /** Function for validation of the platform and network with flatten */
  private def platformNetworkValidation[A](network: String)(
      operation: => Future[Option[A]]
  ): Future[Option[A]] =
    pathValidation(NetworkPath(network, platform))(operation)

  private def pathValidation[A](path: metadata.Path)(
      operation: => Future[Option[A]]
  ): Future[Option[A]] =
    if (metadataService.exists(path))
      operation
    else
      Future.successful(None)

}
