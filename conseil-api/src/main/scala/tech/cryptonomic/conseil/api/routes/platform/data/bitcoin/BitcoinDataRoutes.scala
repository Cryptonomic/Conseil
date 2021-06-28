package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import cats.instances.either._
import cats.instances.future._
import cats.syntax.bitraverse._
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.common.bitcoin.BitcoinTypes.BitcoinBlockHash
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponseWithOutput
import tech.cryptonomic.conseil.common.metadata
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport

import scala.concurrent.{ExecutionContext, Future}

case class BitcoinDataRoutes(
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    operations: BitcoinDataOperations,
    maxQueryResultSize: Int
)(
    implicit apiExecutionContext: ExecutionContext
) extends BitcoinDataHelpers
    with ApiDataRoutes
    with ConseilLogSupport {

  private val platformPath = PlatformPath("bitcoin")

  /** V2 Route implementation for query endpoint */
  override val postRoute: Route = bitcoinQueryEndpoint.implementedByAsync {
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
  private val blocksRoute: Route = bitcoinBlocksEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchBlocks(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for blocks head endpoint */
  private val blocksHeadRoute: Route = bitcoinBlocksHeadEndpoint.implementedByAsync {
    case (network, _) =>
      platformNetworkValidation(network) {
        operations.fetchBlocksHead()
      }
  }

  /** V2 Route implementation for blocks by hash endpoint */
  private val blockByHashRoute: Route = bitcoinBlockByHashEndpoint.implementedByAsync {
    case ((network, hash), _) =>
      platformNetworkValidation(network) {
        operations.fetchBlockByHash(BitcoinBlockHash(hash))
      }
  }

  /** V2 Route implementation for transactions endpoint */
  private val transactionsRoute: Route = bitcoinTransactionsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchTransactions(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for transaction by id endpoint */
  private val transactionByIdRoute: Route = bitcoinTransactionByIdEndpoint.implementedByAsync {
    case ((network, id), _) =>
      platformNetworkValidation(network) {
        operations.fetchTransactionById(id)
      }
  }

  /** V2 Route implementation for inputs endpoint */
  private val inputsRoute: Route = bitcoinInputsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchInputs(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for outputs endpoint */
  private val outputsRoute: Route = bitcoinOutputsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchOutputs(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for accounts endpoint */
  private val accountsRoute: Route = bitcoinAccountsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations.fetchAccounts(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for accounts by address endpoint */
  private val accountsByAddressRoute: Route = bitcoinAccountByAddressEndpoint.implementedByAsync {
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
      inputsRoute,
      outputsRoute,
      accountsRoute,
      accountsByAddressRoute
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
