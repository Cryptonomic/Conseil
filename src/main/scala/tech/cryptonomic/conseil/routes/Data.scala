package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.generic.chain.DataPlatform
import tech.cryptonomic.conseil.tezos.ApiOperations
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.util.ConfigUtil

import scala.concurrent.{ExecutionContext, Future}

/** Companion object providing apply implementation */
object Data {
  def apply(config: PlatformsConfiguration)(implicit ec: ExecutionContext): Data = new Data(config, DataPlatform())
}

/**
  * Platform discovery routes.
  *
  * @param queryProtocolPlatform QueryProtocolPlatform object which checks if platform exists and executes query
  * @param apiExecutionContext   is used to call the async operations exposed by the api service
  */
class Data(config: PlatformsConfiguration, queryProtocolPlatform: DataPlatform)(implicit apiExecutionContext: ExecutionContext)
  extends LazyLogging with DatabaseApiFiltering with DataHelpers {

  import cats.instances.either._
  import cats.instances.future._
  import cats.syntax.bitraverse._
  import cats.instances.option._
  import cats.syntax.traverse._

  /*
   * reuse the same context as the one for ApiOperations calls
   * as long as it doesn't create issues or performance degradation
   */
  override val asyncApiFiltersExecutionContext: ExecutionContext = apiExecutionContext
  /** V2 Route implementation for query endpoint */
  val postRoute: Route = queryEndpoint.implementedByAsync {
    case ((platform, network, entity), apiQuery, _) =>
      apiQuery.validate(entity).map { validQuery =>
        platformNetworkValidation(platform, network) {
          queryProtocolPlatform.queryWithPredicates(platform, entity, validQuery)
        }
      }.left.map(Future.successful).bisequence.map(eitherOptionOps)
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
      platformNetworkValidationFlattened(platform, network) {
        ApiOperations.fetchLatestBlock()
      }
  }

  /** V2 Route implementation for blocks by hash endpoint */
  val blockByHashRoute: Route = blockByHashEndpoint.implementedByAsync {
    case ((platform, network, hash), _) =>
      platformNetworkValidationFlattened(platform, network) {
        ApiOperations.fetchBlock(BlockHash(hash))
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
      platformNetworkValidationFlattened(platform, network) {
        ApiOperations.fetchAccount(AccountId(accountId))
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
      platformNetworkValidationFlattened(platform, network) {
        ApiOperations.fetchOperationGroup(operationGroupId)
      }
  }

  /** V2 Route implementation for average fees endpoint */
  val avgFeesRoute: Route = avgFeesEndpoint.implementedByAsync {
    case ((platform, network, filter), _) =>
      platformNetworkValidationFlattened(platform, network) {
        ApiOperations.fetchAverageFees(filter)
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

  /** Function for validation of the platform and network */
  private def platformNetworkValidation[A](platform: String, network: String)(operation: Option[Future[A]]): Future[Option[A]] = {
    ConfigUtil.getNetworks(config, platform).find(_.network == network).flatMap { _ =>
      operation
    }.sequence
  }

  /** Function for validation of the platform and network with flatten */
  private def platformNetworkValidationFlattened[A](platform: String, network: String)(operation: Future[Option[A]]): Future[Option[A]] = {
    ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
      operation
    }.sequence.map(_.flatten)
  }
}
