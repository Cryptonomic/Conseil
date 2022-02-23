package tech.cryptonomic.conseil.api.platform.data.ethereum

import tech.cryptonomic.conseil.api.platform.metadata.MetadataService
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.ethereum.EthereumTypes.EthereumBlockHash
import tech.cryptonomic.conseil.common.metadata
import tech.cryptonomic.conseil.common.metadata.{NetworkPath, PlatformPath}

import tech.cryptonomic.conseil.api.platform.data.ApiDataRoutes

import scala.concurrent.ExecutionContext

import cats.effect.IO
import cats.syntax.applicative._

import sttp.tapir.Endpoint

/** Trait, which contains routes for Ethereum-related block-chains */
trait EthereumDataRoutesCreator extends EthereumDataEndpoints with ApiDataRoutes {

  implicit def ec: ExecutionContext

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

  /** V2 concatenated routes */
  lazy val getRoute = List(
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

  /** V2 Route implementation for query endpoint */
  // override val postRoute = ethereumQueryEndpoint.serverLogic[IO] {
  //   case (network, entity, apiQuery, ) =>
  //     val path = EntityPath(entity, NetworkPath(network, platformPath))
  //     pathValidation(path) {
  //       apiQuery
  //         .validate(path, metadataService, metadataConfiguration)
  //         .flatMap { validationResult =>
  //           validationResult.map { validQuery =>
  //             operations
  //               .queryWithPredicates(platformPath.platform, entity, validQuery.withLimitCap(maxQueryResultSize))
  //               .map { queryResponses =>
  //                 QueryResponseWithOutput(queryResponses, validQuery.output)
  //               }
  //           }.left.map(Future.successful).bisequence
  //         }
  //         .map(Some(_))
  //     }
  // }

  /** V2 Route implementation for blocks endpoint */
  private val blocksRoute =
    ethereumBlocksEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((filter: EthereumFilter) => operations.fetchBlocks(filter.toQuery.withLimitCap(maxQueryResultSize)))(element)
            .map(Some.apply)
        ).map(_.toRight(()))
    }

  /** V2 Route implementation for blocks head endpoint */
  private lazy val blocksHeadRoute =
    ethereumBlocksHeadEndpoint.serverLogic[IO] {
      case (network, _) =>
        platformNetworkValidation(network)(operations.fetchBlocksHead()).map(_.toRight(()))
    }

  /** V2 Route implementation for blocks by hash endpoint */
  private val blockByHashRoute =
    ethereumBlockByHashEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((hash: String) => operations.fetchBlockByHash(EthereumBlockHash(hash)))(
            element
          )
        ).map(_.toRight(()))
    }

  /** V2 Route implementation for transactions endpoint */
  private val transactionsRoute =
    ethereumTransactionsEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((filter: EthereumFilter) => operations.fetchTransactions(filter.toQuery.withLimitCap(maxQueryResultSize)))(
            element
          ).map(Some.apply)
        ).map(_.toRight(()))
    }

  /** V2 Route implementation for transaction by id endpoint */
  private val transactionByIdRoute =
    ethereumTransactionByHashEndpoint.serverLogic[IO] {
      case (network, _, hash) =>
        platformNetworkValidation(network)(operations.fetchTransactionByHash(hash)).map(_.toRight(()))
    }

  /** V2 Route implementation for logs endpoint */
  private val logsRoute =
    ethereumLogsEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((filter: EthereumFilter) => operations.fetchLogs(filter.toQuery.withLimitCap(maxQueryResultSize)))(element)
            .map(Some.apply)
        ).map(_.toRight(()))
    }

  /** V2 Route implementation for receipts endpoint */
  private val receiptsRoute =
    ethereumReceiptsEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((filter: EthereumFilter) => operations.fetchReceipts(filter.toQuery.withLimitCap(maxQueryResultSize)))(
            element
          ).map(Some.apply)
        ).map(_.toRight(()))
    }

  /** V2 Route implementation for contracts endpoint */
  private val contractsRoute =
    ethereumContractsEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((filter: EthereumFilter) => operations.fetchContracts(filter.toQuery.withLimitCap(maxQueryResultSize)))(
            element
          ).map(Some.apply)
        ).map(_.toRight(()))
    }

  /** V2 Route implementation for tokens endpoint */
  private val tokensRoute =
    ethereumTokensEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((filter: EthereumFilter) => operations.fetchTokens(filter.toQuery.withLimitCap(maxQueryResultSize)))(
            element
          ).map(Some.apply)
        ).map(_.toRight(()))
    }

  /** V2 Route implementation for token transfers endpoint */
  private val tokenTransfersRoute =
    ethereumTokenTransfersEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((filter: EthereumFilter) => operations.fetchTokenTransfers(filter.toQuery.withLimitCap(maxQueryResultSize)))(
            element
          ).map(Some.apply)
        ).map(_.toRight(()))
    }

  /** V2 Route implementation for tokens history endpoint */
  private val tokensHistoryRoute =
    ethereumTokensHistoryEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((filter: EthereumFilter) => operations.fetchTokensHistory(filter.toQuery.withLimitCap(maxQueryResultSize)))(
            element
          ).map(Some.apply)
        ).map(_.toRight(()))
    }

  /** V2 Route implementation for accounts endpoint */
  private val accountsRoute =
    ethereumAccountsEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((filter: EthereumFilter) => operations.fetchAccounts(filter.toQuery.withLimitCap(maxQueryResultSize)))(
            element
          ).map(Some.apply)
        ).map(_.toRight(()))
    }

  /** V2 Route implementation for accounts by address endpoint */
  private val accountsByAddressRoute =
    ethereumAccountByAddressEndpoint.serverLogic[IO] {
      case (network, _, address) =>
        platformNetworkValidation(network)(operations.fetchAccountByAddress(address)).map(_.toRight(()))
    }

  /** V2 Route implementation for accounts history endpoint */
  private val accountsHistoryRoute =
    ethereumAccountsHistoryEndpoint.serverLogic[IO] {
      case (network, _, element) =>
        platformNetworkValidation(network)(
          ((filter: EthereumFilter) => operations.fetchAccountsHistory(filter.toQuery.withLimitCap(maxQueryResultSize)))(
            element
          ).map(Some.apply)
        ).map(_.toRight(()))
    }

  /**
    * Helper method, which validates the network and delegates the call for specific endpoint to the given method `f`.
    * Note that this method works only for Tuple2 input parameters in the `endpoint`.
    */
  // private
  def delegateCall0[B](
      endpoint: Endpoint[Unit, (String, Option[String]), Unit, Option[B], Any]
  )(f: IO[Option[B]]) =
    endpoint.serverLogic[IO] {
      case (network, _) => platformNetworkValidation(network)(f).map(Right.apply)
    }

  /**
    * Helper method, which validates the network and delegates the call for specific endpoint to the given method `f`.
    * Note that this method works for more generic input type in the `endpoint`.
    */
  // private
  def delegateCall[A, B](
      endpoint: Endpoint[Unit, ((String, A), Option[String]), Unit, Option[B], Any]
  )(f: A => IO[Option[B]]) =
    endpoint.serverLogic[IO] {
      case ((network, element), _) => platformNetworkValidation(network)(f(element)).map(Right.apply)
    }

  /** Function for validation of the platform and network with flatten */
  private def platformNetworkValidation[A](network: String)(operation: IO[Option[A]]): IO[Option[A]] =
    pathValidation(NetworkPath(network, platformPath))(operation)

  private def pathValidation[A](path: metadata.Path)(operation: IO[Option[A]]): IO[Option[A]] =
    metadataService.exists(path).flatMap(if (_) operation else None.pure[IO])

}
