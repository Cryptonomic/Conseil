package tech.cryptonomic.conseil.api.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.common.config.{MetadataConfiguration, ServerConfiguration}
import tech.cryptonomic.conseil.common.generic.chain.DataPlatform
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponseWithOutput
import tech.cryptonomic.conseil.common.metadata
import tech.cryptonomic.conseil.common.metadata.{EntityPath, MetadataService, NetworkPath, PlatformPath}
import tech.cryptonomic.conseil.common.tezos.ApiOperations
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{AccountId, BlockHash}

import scala.concurrent.{ExecutionContext, Future}

/** Companion object providing apply implementation */
object Data {
  def apply(
      metadataService: MetadataService,
      server: ServerConfiguration,
      metadataConfiguration: MetadataConfiguration,
      apiOperations: ApiOperations
  )(
      implicit ec: ExecutionContext
  ): Data =
    new Data(
      DataPlatform(apiOperations, server.maxQueryResultSize),
      metadataService,
      metadataConfiguration: MetadataConfiguration,
      apiOperations: ApiOperations
    )
}

/**
  * Platform discovery routes.
  *
  * @param queryProtocolPlatform QueryProtocolPlatform object which checks if platform exists and executes query
  * @param apiExecutionContext   is used to call the async operations exposed by the api service
  */
class Data(
    queryProtocolPlatform: DataPlatform,
    metadataService: MetadataService,
    metadataConfiguration: MetadataConfiguration,
    apiOperations: ApiOperations
)(
    implicit apiExecutionContext: ExecutionContext
) extends LazyLogging
    with DataHelpers {

  import cats.instances.either._
  import cats.instances.future._
  import cats.syntax.bitraverse._

  /** V2 Route implementation for query endpoint */
  val postRoute: Route = queryEndpoint.implementedByAsync {
    case ((platform, network, entity), apiQuery, _) =>
      val path = EntityPath(entity, NetworkPath(network, PlatformPath(platform)))

      pathValidation(path) {
        apiQuery.validate(path, metadataService, metadataConfiguration).flatMap { validationResult =>
          validationResult.map { validQuery =>
            queryProtocolPlatform.queryWithPredicates(platform, entity, validQuery).map { queryResponseOpt =>
              queryResponseOpt.map { queryResponses =>
                QueryResponseWithOutput(
                  queryResponses,
                  validQuery.output
                )
              }
            }
          }.left.map(Future.successful).bisequence.map(eitherOptionOps)
        }
      }
  }

  /** V2 Route implementation for blocks endpoint */
  val blocksRoute: Route = blocksEndpoint.implementedByAsync {
    case ((platform, network, filter), _) =>
      platformNetworkValidation(platform, network) {
        queryProtocolPlatform.queryWithPredicates(platform, "blocks", filter.toQuery)
      }
  }

  /** V2 Route implementation for blocks head endpoint */
  val blocksHeadRoute: Route = blocksHeadEndpoint.implementedByAsync {
    case (platform, network, _) =>
      platformNetworkValidation(platform, network) {
        apiOperations.fetchLatestBlock()
      }
  }

  /** V2 Route implementation for blocks by hash endpoint */
  val blockByHashRoute: Route = blockByHashEndpoint.implementedByAsync {
    case ((platform, network, hash), _) =>
      platformNetworkValidation(platform, network) {
        apiOperations.fetchBlock(BlockHash(hash))
      }
  }

  /** V2 Route implementation for accounts endpoint */
  val accountsRoute: Route = accountsEndpoint.implementedByAsync {
    case ((platform, network, filter), _) =>
      platformNetworkValidation(platform, network) {
        queryProtocolPlatform.queryWithPredicates(platform, "accounts", filter.toQuery)
      }
  }

  /** V2 Route implementation for account by ID endpoint */
  val accountByIdRoute: Route = accountByIdEndpoint.implementedByAsync {
    case ((platform, network, accountId), _) =>
      platformNetworkValidation(platform, network) {
        apiOperations.fetchAccount(AccountId(accountId))
      }
  }

  /** V2 Route implementation for operation groups endpoint */
  val operationGroupsRoute: Route = operationGroupsEndpoint.implementedByAsync {
    case ((platform, network, filter), _) =>
      platformNetworkValidation(platform, network) {
        queryProtocolPlatform.queryWithPredicates(platform, "operation_groups", filter.toQuery)
      }
  }

  /** V2 Route implementation for operation group by ID endpoint */
  val operationGroupByIdRoute: Route = operationGroupByIdEndpoint.implementedByAsync {
    case ((platform, network, operationGroupId), _) =>
      platformNetworkValidation(platform, network) {
        apiOperations.fetchOperationGroup(operationGroupId)
      }
  }

  /** V2 Route implementation for average fees endpoint */
  val avgFeesRoute: Route = avgFeesEndpoint.implementedByAsync {
    case ((platform, network, filter), _) =>
      //to simplify working on Future[Option[Somedata]]
      import cats.data.OptionT
      platformNetworkValidation(platform, network) {
        (for {
          queryResponses <- OptionT(queryProtocolPlatform.queryWithPredicates(platform, "fees", filter.toQuery))
          first <- OptionT.fromOption[Future](queryResponses.headOption)
        } yield first).value
      }
  }

  /** V2 Route implementation for operations endpoint */
  val operationsRoute: Route = operationsEndpoint.implementedByAsync {
    case ((platform, network, filter), _) =>
      platformNetworkValidation(platform, network) {
        queryProtocolPlatform.queryWithPredicates(platform, "operations", filter.toQuery)
      }
  }

  /** V2 concatenated routes */
  val getRoute: Route = concat(
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
  private def platformNetworkValidation[A](platform: String, network: String)(
      operation: => Future[Option[A]]
  ): Future[Option[A]] =
    pathValidation(NetworkPath(network, PlatformPath(platform)))(operation)

  private def pathValidation[A](path: metadata.Path)(
      operation: => Future[Option[A]]
  ): Future[Option[A]] =
    if (metadataService.exists(path))
      operation
    else
      Future.successful(None)
}
