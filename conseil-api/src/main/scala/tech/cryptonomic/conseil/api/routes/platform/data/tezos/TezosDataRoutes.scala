package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponseWithOutput
import tech.cryptonomic.conseil.common.metadata
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{AccountId, TezosBlockHash}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Platform discovery routes.
  * @param apiExecutionContext   is used to call the async operations exposed by the api service
  */
case class TezosDataRoutes(
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    operations: TezosDataOperations,
    maxQueryResultSize: Int
)(
    implicit apiExecutionContext: ExecutionContext
) extends TezosDataHelpers
    with ApiDataRoutes
    with LazyLogging {

  import cats.instances.either._
  import cats.instances.future._
  import cats.syntax.bitraverse._

  private val platformPath = PlatformPath("tezos")

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
  private val blocksRoute: Route = tezosBlocksEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations
          .queryWithPredicates("tezos", "blocks", filter.toQuery.withLimitCap(maxQueryResultSize))
          .map(Option(_))
      }
  }

  /** V2 Route implementation for blocks head endpoint */
  private val blocksHeadRoute: Route = tezosBlocksHeadEndpoint.implementedByAsync {
    case (network, _) =>
      platformNetworkValidation(network) {
        operations.fetchLatestBlock()
      }
  }

  /** V2 Route implementation for blocks by hash endpoint */
  private val blockByHashRoute: Route = tezosBlockByHashEndpoint.implementedByAsync {
    case ((network, hash), _) =>
      platformNetworkValidation(network) {
        operations.fetchBlock(TezosBlockHash(hash))
      }
  }

  /** V2 Route implementation for accounts endpoint */
  private val accountsRoute: Route = tezosAccountsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations
          .queryWithPredicates("tezos", "accounts", filter.toQuery.withLimitCap(maxQueryResultSize))
          .map(Some(_))
      }
  }

  /** V2 Route implementation for account by ID endpoint */
  private val accountByIdRoute: Route = tezosAccountByIdEndpoint.implementedByAsync {
    case ((network, accountId), _) =>
      platformNetworkValidation(network) {
        operations.fetchAccount(AccountId(accountId))
      }
  }

  /** V2 Route implementation for operation groups endpoint */
  private val operationGroupsRoute: Route = tezosOperationGroupsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations
          .queryWithPredicates("tezos", "operation_groups", filter.toQuery.withLimitCap(maxQueryResultSize))
          .map(Some(_))
      }
  }

  /** V2 Route implementation for operation group by ID endpoint */
  private val operationGroupByIdRoute: Route = tezosOperationGroupByIdEndpoint.implementedByAsync {
    case ((network, operationGroupId), _) =>
      platformNetworkValidation(network) {
        operations.fetchOperationGroup(operationGroupId)
      }
  }

  /** V2 Route implementation for average fees endpoint */
  private val avgFeesRoute: Route = tezosAvgFeesEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations
          .queryWithPredicates("tezos", "fees", filter.toQuery.withLimitCap(maxQueryResultSize))
          .map(_.headOption)
      }
  }

  /** V2 Route implementation for operations endpoint */
  private val operationsRoute: Route = tezosOperationsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      platformNetworkValidation(network) {
        operations
          .queryWithPredicates("tezos", "operations", filter.toQuery.withLimitCap(maxQueryResultSize))
          .map(Some(_))
      }
  }

  /** V2 concatenated routes */
  override val getRoute: Route = concat(
    blocksHeadRoute,
    blockByHashRoute,
    blocksRoute,
    accountByIdRoute,
    accountsRoute,
    operationGroupByIdRoute,
    operationGroupsRoute,
    avgFeesRoute,
    operationsRoute
  )

  /** Function for validation of the platform and network with flatten */
  private def platformNetworkValidation[A](network: String)(operation: => Future[Option[A]]): Future[Option[A]] =
    pathValidation(NetworkPath(network, platformPath))(operation)

  private def pathValidation[A](path: metadata.Path)(
      operation: => Future[Option[A]]
  ): Future[Option[A]] =
    if (metadataService.exists(path))
      operation
    else
      Future.successful(None)
}
