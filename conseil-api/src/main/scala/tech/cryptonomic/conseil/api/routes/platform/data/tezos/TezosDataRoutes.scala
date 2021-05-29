package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponseWithOutput
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.metadata
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{makeAccountId, TezosBlockHash}
import tech.cryptonomic.conseil.common.tezos.Tables

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
    with ConseilLogSupport {

  import cats.instances.either._
  import cats.instances.future._
  import cats.syntax.bitraverse._

  private val platformPath = PlatformPath("tezos")

  /* Collects the names of any entity which could be invalidated by a fork
   * Currently it's a static list of table names, it should be loaded
   * dynamically and memoized from the actual db-schema, to avoid
   * maintenance issues.
   * The logic would be to scan all tables and verify which one has a specific
   * column named as [[TezosDataOperations.InvalidationAwareAttribute]]
   */
  private lazy val forkAwareEntities = Set(
    Tables.Accounts.baseTableRow.tableName,
    Tables.AccountsHistory.baseTableRow.tableName,
    Tables.Blocks.baseTableRow.tableName,
    Tables.Governance.baseTableRow.tableName,
    Tables.TokenBalances.baseTableRow.tableName,
    Tables.BakingRights.baseTableRow.tableName,
    Tables.EndorsingRights.baseTableRow.tableName,
    Tables.BalanceUpdates.baseTableRow.tableName,
    Tables.Bakers.baseTableRow.tableName,
    Tables.BakersHistory.baseTableRow.tableName,
    Tables.Fees.baseTableRow.tableName,
    Tables.OperationGroups.baseTableRow.tableName,
    Tables.Operations.baseTableRow.tableName
  )

  /** V2 Route implementation for query endpoint */
  override def postRoute: Route = queryEndpoint(platformPath).implementedByAsync {
    case (network, entity, apiQuery, _) =>
      val path = EntityPath(entity, NetworkPath(network, platformPath))

      pathValidation(path) {
        shouldHideForkEntries(entity)(
          hideForkData =>
            apiQuery
              .validate(path, metadataService, metadataConfiguration)
              .flatMap { validationResult =>
                validationResult.map { validQuery =>
                  operations
                    .queryWithPredicates(
                      platformPath.platform,
                      entity,
                      validQuery.withLimitCap(maxQueryResultSize),
                      hideForkData
                    )
                    .map { queryResponses =>
                      QueryResponseWithOutput(queryResponses, validQuery.output)
                    }
                }.left.map(Future.successful).bisequence
              }
              .map(Some(_))
        )
      }
  }

  /* will provide an async operation with the information if this entity needs checking for fork-invalidation */
  private def shouldHideForkEntries[T](entity: String)(asyncCall: Boolean => Future[T]): Future[T] =
    asyncCall(forkAwareEntities.contains(entity))

  /* reuse the query route (POST) logic for entity-specific filtered endpoints */
  private def routeFromQuery[B](network: String, entity: String, filter: TezosFilter)(
      handleResult: List[QueryResponse] => Option[B]
  ): Future[Option[B]] =
    shouldHideForkEntries(entity)(
      hideForkData =>
        platformNetworkValidation(network) {
          operations
            .queryWithPredicates("tezos", entity, filter.toQuery.withLimitCap(maxQueryResultSize), hideForkData)
            .map(handleResult)
        }
    )

  /** V2 Route implementation for blocks endpoint */
  private val blocksRoute: Route = tezosBlocksEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      routeFromQuery(network, "blocks", filter) { Option(_) }
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
      routeFromQuery(network, "accounts", filter) { Some(_) }
  }

  /** V2 Route implementation for account by ID endpoint */
  private val accountByIdRoute: Route = tezosAccountByIdEndpoint.implementedByAsync {
    case ((network, accountId), _) =>
      platformNetworkValidation(network) {
        operations.fetchAccount(makeAccountId(accountId))
      }
  }

  /** V2 Route implementation for operation groups endpoint */
  private val operationGroupsRoute: Route = tezosOperationGroupsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      routeFromQuery(network, "operation_groups", filter) { Some(_) }
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
      routeFromQuery(network, "fees", filter) { _.headOption }
  }

  /** V2 Route implementation for operations endpoint */
  private val operationsRoute: Route = tezosOperationsEndpoint.implementedByAsync {
    case ((network, filter), _) =>
      routeFromQuery(network, "operations", filter) { Some(_) }
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
