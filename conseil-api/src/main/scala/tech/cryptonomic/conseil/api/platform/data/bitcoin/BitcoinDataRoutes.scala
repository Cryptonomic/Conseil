package tech.cryptonomic.conseil.api.platform.data.bitcoin

import tech.cryptonomic.conseil.api.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.api.platform.metadata.MetadataService
import tech.cryptonomic.conseil.common.bitcoin.BitcoinTypes.BitcoinBlockHash
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.metadata.{NetworkPath, Path, PlatformPath}

import scala.concurrent.ExecutionContext

import cats.effect.IO
import cats.syntax.applicative._

case class BitcoinDataRoutes(
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    operations: BitcoinDataOperations,
    maxQueryResultSize: Int
)(implicit apiExecutionContext: ExecutionContext)
    extends BitcoinDataEndpoints
    with ApiDataRoutes {

  private val platformPath = PlatformPath("bitcoin")

  /** V2 Route implementation for query endpoint */
  // override val postRoute = bitcoinQueryEndpoint.implementedByAsync {
  //   case (network, entity, apiQuery, _) =>
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
  private val blocksRoute = bitcoinBlocksEndpoint
    // .serverSecurityLogicPure(apiKey => apiKey.filter(_ == "apiKey").toRight(()))
    .serverLogic[IO] { case (network, filter) =>
      platformNetworkValidation(network) {
        operations.fetchBlocks(filter.toQuery.withLimitCap(maxQueryResultSize)).map(Some.apply)
      }.map(_.toRight(()))
    }

  /** V2 Route implementation for blocks head endpoint */
  private val blocksHeadRoute = bitcoinBlocksHeadEndpoint.serverLogic[IO] { network =>
    platformNetworkValidation(network) {
      operations.fetchBlocksHead()
    }.map(_.toRight(()))
  }

  /** V2 Route implementation for blocks by hash endpoint */
  private val blockByHashRoute = bitcoinBlockByHashEndpoint.serverLogic[IO] { case (network, hash) =>
    platformNetworkValidation(network) {
      operations.fetchBlockByHash(BitcoinBlockHash(hash))
    }.map(_.toRight(()))
  }

  /** V2 Route implementation for transactions endpoint */
  private val transactionsRoute = bitcoinTransactionsEndpoint.serverLogic[IO] { case (network, filter) =>
    platformNetworkValidation(network) {
      operations.fetchTransactions(filter.toQuery.withLimitCap(maxQueryResultSize)).map(Some.apply)
    }.map(_.toRight(()))
  }

  /** V2 Route implementation for transaction by id endpoint */
  private val transactionByIdRoute = bitcoinTransactionByIdEndpoint.serverLogic[IO] { case (network, id) =>
    platformNetworkValidation(network) {
      operations.fetchTransactionById(id)
    }.map(_.toRight(()))
  }

  /** V2 Route implementation for inputs endpoint */
  private val inputsRoute = bitcoinInputsEndpoint.serverLogic[IO] { case (network, filter) =>
    platformNetworkValidation(network) {
      operations.fetchInputs(filter.toQuery.withLimitCap(maxQueryResultSize)).map(Some.apply)
    }.map(_.toRight(()))
  }

  /** V2 Route implementation for outputs endpoint */
  private val outputsRoute = bitcoinOutputsEndpoint.serverLogic[IO] { case (network, filter) =>
    platformNetworkValidation(network) {
      operations
        .fetchOutputs(filter.toQuery.withLimitCap(maxQueryResultSize))
        .map(Some.apply)
    }.map(_.toRight(()))
  }

  /** V2 Route implementation for accounts endpoint */
  private val accountsRoute = bitcoinAccountsEndpoint.serverLogic[IO] { case (network, filter) =>
    platformNetworkValidation(network) {
      operations.fetchAccounts(filter.toQuery.withLimitCap(maxQueryResultSize)).map(Some.apply)
    }.map(_.toRight(()))
  }

  /** V2 Route implementation for accounts by address endpoint */
  private val accountsByAddressRoute = bitcoinAccountByAddressEndpoint.serverLogic[IO] { case (network, address) =>
    platformNetworkValidation(network) {
      operations.fetchAccountByAddress(address)
    }.map(_.toRight(()))
  }

  /** V2 concatenated routes */
  lazy val getRoute = List(
    blocksHeadRoute,
    blockByHashRoute,
    blocksRoute,
    transactionsRoute,
    transactionByIdRoute,
    inputsRoute,
    outputsRoute,
    accountsRoute,
    accountsByAddressRoute
  )

  /** Function for validation of the platform and network with flatten */
  private def platformNetworkValidation[A](network: String)(operation: IO[Option[A]]): IO[Option[A]] =
    pathValidation(NetworkPath(network, platformPath))(operation)

  private def pathValidation[A](path: Path)(operation: IO[Option[A]]): IO[Option[A]] =
    metadataService.exists(path).flatMap(if (_) operation else None.pure[IO])

}