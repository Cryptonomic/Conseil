package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import cats.instances.either._
import cats.instances.future._
import cats.syntax.bitraverse._
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
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
    with ConseilLogSupport {

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
  def platformPath: PlatformPath

  /** V2 Route implementation for query endpoint */
  override val postRoute: Route = ethereumQueryEndpoint.implementedByAsync {
    case (network, entity, apiQuery, _) =>
      val path = EntityPath(entity, NetworkPath(network, platformPath))
      pathValidation(path) {
        apiQuery
          .validate(path, metadataService, metadataConfiguration)
          .flatMap { validationResult =>
            validationResult.map { validQuery =>
              operations
                .queryWithPredicates(platformPath.platform, entity, validQuery.withLimitCap(maxQueryResultSize))
                .map { queryResponses =>
                  QueryResponseWithOutput(queryResponses, validQuery.output)
                }
            }.left.map(Future.successful).bisequence
          }
          .map(Some(_))
      }
  }

  /** V2 Route implementation for blocks endpoint */
  private val blocksRoute: Route = delegateCall(ethereumBlocksEndpoint)(
    filter => operations.fetchBlocks(filter.toQuery.withLimitCap(maxQueryResultSize))
  )

  /** V2 Route implementation for blocks head endpoint */
  private val blocksHeadRoute: Route = delegateCall0(ethereumBlocksHeadEndpoint)(operations.fetchBlocksHead)

  /** V2 Route implementation for blocks by hash endpoint */
  private val blockByHashRoute: Route =
    delegateCall(ethereumBlockByHashEndpoint)(hash => operations.fetchBlockByHash(EthereumBlockHash(hash)))

  /** V2 Route implementation for transactions endpoint */
  private val transactionsRoute: Route = delegateCall(ethereumTransactionsEndpoint)(
    filter => operations.fetchTransactions(filter.toQuery.withLimitCap(maxQueryResultSize))
  )

  /** V2 Route implementation for transaction by id endpoint */
  private val transactionByIdRoute: Route =
    delegateCall(ethereumTransactionByHashEndpoint)(operations.fetchTransactionByHash)

  /** V2 Route implementation for logs endpoint */
  private val logsRoute: Route =
    delegateCall(ethereumLogsEndpoint)(filter => operations.fetchLogs(filter.toQuery.withLimitCap(maxQueryResultSize)))

  /** V2 Route implementation for receipts endpoint */
  private val receiptsRoute: Route = delegateCall(ethereumReceiptsEndpoint)(
    filter => operations.fetchReceipts(filter.toQuery.withLimitCap(maxQueryResultSize))
  )

  /** V2 Route implementation for contracts endpoint */
  private val contractsRoute: Route = delegateCall(ethereumContractsEndpoint)(
    filter => operations.fetchContracts(filter.toQuery.withLimitCap(maxQueryResultSize))
  )

  /** V2 Route implementation for tokens endpoint */
  private val tokensRoute: Route = delegateCall(ethereumTokensEndpoint)(
    filter => operations.fetchTokens(filter.toQuery.withLimitCap(maxQueryResultSize))
  )

  /** V2 Route implementation for token transfers endpoint */
  private val tokenTransfersRoute: Route = delegateCall(ethereumTokenTransfersEndpoint)(
    filter => operations.fetchTokenTransfers(filter.toQuery.withLimitCap(maxQueryResultSize))
  )

  /** V2 Route implementation for tokens history endpoint */
  private val tokensHistoryRoute: Route = delegateCall(ethereumTokensHistoryEndpoint)(
    filter => operations.fetchTokensHistory(filter.toQuery.withLimitCap(maxQueryResultSize))
  )

  /** V2 Route implementation for accounts endpoint */
  private val accountsRoute: Route = delegateCall(ethereumAccountsEndpoint)(
    filter => operations.fetchAccounts(filter.toQuery.withLimitCap(maxQueryResultSize))
  )

  /** V2 Route implementation for accounts by address endpoint */
  private val accountsByAddressRoute: Route =
    delegateCall(ethereumAccountByAddressEndpoint)(operations.fetchAccountByAddress)

  /** V2 Route implementation for accounts history endpoint */
  private val accountsHistoryRoute: Route = delegateCall(ethereumAccountsHistoryEndpoint)(
    filter => operations.fetchAccountsHistory(filter.toQuery.withLimitCap(maxQueryResultSize))
  )

  /**
    * Helper method, which validates the network and delegates the call for specific endpoint to the given method `f`.
    * Note that this method works only for Tuple2 input parameters in the `endpoint`.
    **/
  private def delegateCall0[B](
      endpoint: Endpoint[(String, Option[String]), Option[B]]
  )(f: () => Future[Option[B]]): Route =
    endpoint.implementedByAsync {
      case (network, _) => platformNetworkValidation(network)(f())
    }

  /**
    * Helper method, which validates the network and delegates the call for specific endpoint to the given method `f`.
    * Note that this method works for more generic input type in the `endpoint`.
    **/
  private def delegateCall[A, B](
      endpoint: Endpoint[((String, A), Option[String]), Option[B]]
  )(f: A => Future[Option[B]]): Route =
    endpoint.implementedByAsync {
      case ((network, element), _) => platformNetworkValidation(network)(f(element))
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
      tokensHistoryRoute,
      accountsRoute,
      accountsByAddressRoute,
      accountsHistoryRoute
    )

  /** Function for validation of the platform and network with flatten */
  private def platformNetworkValidation[A](network: String)(
      operation: => Future[Option[A]]
  ): Future[Option[A]] =
    pathValidation(NetworkPath(network, platformPath))(operation)

  private def pathValidation[A](path: metadata.Path)(
      operation: => Future[Option[A]]
  ): Future[Option[A]] =
    if (metadataService.exists(path))
      operation
    else
      Future.successful(None)

}
