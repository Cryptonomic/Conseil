package tech.cryptonomic.conseil.api.platform.data.tezos

import tech.cryptonomic.conseil.api.platform.metadata.MetadataService
import tech.cryptonomic.conseil.api.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.metadata._
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{makeAccountId, TezosBlockHash}
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.util.syntax._

import scala.concurrent.ExecutionContext
import cats.effect.IO
import cats.syntax.either._

/**
  * Platform discovery routes.
  * @param apiExecutionContext   is used to call the async operations exposed by the api service
  */
case class TezosDataRoutes(
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    operations: TezosDataOperations,
    maxQueryResultSize: Int
)(implicit apiExecutionContext: ExecutionContext)
    extends TezosDataEndpoints
    with ApiDataRoutes {

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

  lazy val getRoute = List(
    blocksRoute,
    blocksHeadRoute,
    blockByHashRoute,
    accountsRoute,
    accountByIdRoute,
    operationGroupsRoute,
    operationGroupByIdRoute,
    avgFeesRoute,
    operationsRoute
  )

  /** V2 Route implementation for query endpoint */
  // override val postRoute: Route = tezosQueryEndpoint.implementedByAsync {
  //   case (network, entity, apiQuery, _) =>
  //     val path = EntityPath(entity, NetworkPath(network, platformPath))

  //     pathValidation(path) {
  //       shouldHideForkEntries(entity)(
  //         hideForkData =>
  //           apiQuery
  //             .validate(path, metadataService, metadataConfiguration)
  //             .flatMap { validationResult =>
  //               validationResult.map { validQuery =>
  //                 operations
  //                   .queryWithPredicates(
  //                     platformPath.platform,
  //                     entity,
  //                     validQuery.withLimitCap(maxQueryResultSize),
  //                     hideForkData
  //                   )
  //                   .map { queryResponses =>
  //                     QueryResponseWithOutput(queryResponses, validQuery.output)
  //                   }
  //               }.left.map(Future.successful).bisequence
  //             }
  //             .map(Some(_))
  //       )
  //     }
  // }

  /** will provide an async operation with the information if this entity needs checking for fork-invalidation */
  private def shouldHideForkEntries[T](entity: String)(asyncCall: Boolean => IO[T]): IO[T] =
    asyncCall(forkAwareEntities.contains(entity))

  /** reuse the query route (POST) logic for entity-specific filtered endpoints */
  private def routeFromQuery[A](network: String, entity: String, filter: TezosFilter)(
      handleResult: List[QueryResponse] => Option[A]
  ): IO[Option[A]] =
    shouldHideForkEntries(entity)(
      hideForkData =>
        platformNetworkValidation(network)(
          operations
            .queryWithPredicates("tezos", entity, filter.toQuery.withLimitCap(maxQueryResultSize), hideForkData)
            .toIO
            .map(handleResult)
        )
    )

  /** V2 Route implementation for blocks endpoint */
  lazy val blocksRoute = tezosBlocksEndpoint.serverLogic[IO] {
    case (network: String, filter: TezosFilter, _) =>
      routeFromQuery(network, "blocks", filter)(Option.apply)
        .map(_.getOrElse(Nil).asRight)
  }

  /** V2 Route implementation for blocks head endpoint */
  private val blocksHeadRoute = tezosBlocksHeadEndpoint.serverLogic[IO] {
    case (network, _) =>
      platformNetworkValidation(network)(operations.fetchLatestBlock()).map {
        case Some(value) => Right(value)
        case None => // FIXME: how to encluse it as [Left] properly
          // throw new RuntimeException("oh noes, can't get the head of [BlocksRow]")
          // IO.raiseError(new RuntimeException("oh noes"))
          Left(new RuntimeException("oh noes"))
      }
  }

  /** V2 Route implementation for blocks by hash endpoint */
  private val blockByHashRoute = tezosBlockByHashEndpoint.serverLogic[IO] {
    case (network, hash) =>
      platformNetworkValidation(network)(operations.fetchBlock(TezosBlockHash(hash))).map {
        case None => Left(())
        case Some(value) => Right(value)
      }
  }

  /** V2 Route implementation for accounts endpoint */
  private val accountsRoute = tezosAccountsEndpoint.serverLogic[IO] {
    case (network, filter) =>
      routeFromQuery(network, "accounts", filter)(Some.apply).map {
        case None => Left(())
        case Some(value) => Right(value)
      }
  }

  /** V2 Route implementation for account by ID endpoint */
  private val accountByIdRoute = tezosAccountByIdEndpoint.serverLogic[IO] {
    case (network, accountId) =>
      platformNetworkValidation(network)(operations.fetchAccount(makeAccountId(accountId))).map {
        case None => Left(())
        case Some(value) => Right(value)
      }
  }

  /** V2 Route implementation for operation groups endpoint */
  private val operationGroupsRoute = tezosOperationGroupsEndpoint.serverLogic[IO] {
    case (network, filter) =>
      routeFromQuery(network, "operation_groups", filter)(Some.apply).map {
        case None => Left(())
        case Some(value) => Right(value)
      }
  }

  /** V2 Route implementation for operation group by ID endpoint */
  private val operationGroupByIdRoute = tezosOperationGroupByIdEndpoint.serverLogic[IO] {
    case (network, operationGroupId) =>
      platformNetworkValidation(network)(operations.fetchOperationGroup(operationGroupId)).map {
        case None => Left(())
        case Some(value) => Right(value)
      }
  }

  /** V2 Route implementation for average fees endpoint */
  private val avgFeesRoute = tezosAvgFeesEndpoint.serverLogic[IO] {
    case (network, filter) =>
      routeFromQuery(network, "fees", filter)(_.headOption).map {
        case None => Left(())
        case Some(value) => Right(value)
      }
  }

  /** V2 Route implementation for operations endpoint */
  private val operationsRoute = tezosOperationsEndpoint.serverLogic[IO] {
    case (network, filter) =>
      routeFromQuery(network, "operations", filter)(Some.apply).map {
        case None => Left(())
        case Some(value) => Right(value)
      }
  }

  /** Function for validation of the platform and network with flatten */
  private def platformNetworkValidation[A](network: String)(operation: IO[Option[A]]) = // already by name in IO
    pathValidation(NetworkPath(network, platformPath))(operation)

  private def pathValidation[A](path: Path)(operation: IO[Option[A]]): IO[Option[A]] =
    metadataService.exists(path).flatMap(if (_) operation else IO.none)
}
