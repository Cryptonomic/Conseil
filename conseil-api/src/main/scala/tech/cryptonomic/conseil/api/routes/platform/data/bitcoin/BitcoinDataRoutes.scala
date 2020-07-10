package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import cats.instances.either._
import cats.instances.future._
import cats.syntax.bitraverse._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.{ApiDataOperations, ApiDataRoutes}
import tech.cryptonomic.conseil.common.bitcoin.BitcoinTypes.BitcoinBlockHash
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponseWithOutput
import tech.cryptonomic.conseil.common.metadata
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}

import scala.concurrent.{ExecutionContext, Future}

case class BitcoinDataRoutes(
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    operations: ApiDataOperations,
    maxQueryResultSize: Int
)(
    implicit apiExecutionContext: ExecutionContext
) extends BitcoinDataHelpers
    with ApiDataRoutes
    with LazyLogging {

  private val platformPath = PlatformPath("bitcoin")
  private val dataQueries = new BitcoinDataQueries(operations)

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
  private val blocksRoute: Route = bitcoinBlocksEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        dataQueries.fetchBlocks(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for blocks head endpoint */
  private val blocksHeadRoute: Route = bitcoinBlocksHeadEndpoint.implementedByAsync {
    case (network, _) =>
      platformNetworkValidation(network) {
        dataQueries.fetchBlocksHead()
      }
  }

  /** V2 Route implementation for blocks by hash endpoint */
  private val blockByHashRoute: Route = bitcoinBlockByHashEndpoint.implementedByAsync {
    case ((network, hash), _) =>
      platformNetworkValidation(network) {
        dataQueries.fetchBlockByHash(BitcoinBlockHash(hash))
      }
  }

  /** V2 Route implementation for transactions endpoint */
  private val transactionsRoute: Route = bitcoinTransactionsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        dataQueries.fetchTransactions(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for transaction by id endpoint */
  private val transactionByIdRoute: Route = bitcoinTransactionByIdEndpoint.implementedByAsync {
    case ((network, id), _) =>
      platformNetworkValidation(network) {
        dataQueries.fetchTransactionById(id)
      }
  }

  /** V2 Route implementation for inputs endpoint */
  private val inputsRoute: Route = bitcoinInputsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        dataQueries.fetchInputs(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for outputs endpoint */
  private val outputsRoute: Route = bitcoinOutputsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        dataQueries.fetchOutputs(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for accounts endpoint */
  private val accountsRoute: Route = bitcoinAccountsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        dataQueries.fetchAccounts(filter.toQuery.withLimitCap(maxQueryResultSize))
      }
  }

  /** V2 Route implementation for accounts by address endpoint */
  private val accountsByAddressRoute: Route = bitcoinAccountByAddressEndpoint.implementedByAsync {
    case ((network, address), _) =>
      platformNetworkValidation(network) {
        dataQueries.fetchAccountByAddress(address)
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
