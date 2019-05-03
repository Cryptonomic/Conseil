package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Route}
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos._
import tech.cryptonomic.conseil.tezos.ApiTypes.Filter
import tech.cryptonomic.conseil.tezos.TezosTypes.{BlockHash, AccountId}
import endpoints.akkahttp
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.routes.openapi.TezosEndpoints
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.tezos._
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore

import scala.concurrent.ExecutionContext

/** Provides useful route and directive definitions */
object Tezos {

  def apply(implicit apiExecutionContext: ExecutionContext) = new Tezos

  // Directive for extracting out filter parameters for most GET operations.
  val gatherConseilFilter: Directive[Tuple1[Filter]] = parameters(
    "limit".as[Int].?,
    "block_id".as[String].*,
    "block_level".as[Int].*,
    "block_netid".as[String].*,
    "block_protocol".as[String].*,
    "operation_id".as[String].*,
    "operation_source".as[String].*,
    "operation_destination".as[String].*,
    "operation_participant".as[String].*,
    "operation_kind".as[String].*,
    "account_id".as[String].*,
    "account_manager".as[String].*,
    "account_delegate".as[String].*,
    "sort_by".as[String].?,
    "order".as[String].?
  ).as(Filter.readParams _)

  // Directive for gathering account information for most POST operations.
  val gatherKeyInfo: Directive[Tuple1[KeyStore]] = parameters(
    "publicKey".as[String],
    "privateKey".as[String],
    "publicKeyHash".as[String]
  ).tflatMap{
    case (publicKey, privateKey, publicKeyHash) =>
    val keyStore = KeyStore(publicKey = publicKey, privateKey = privateKey, publicKeyHash = publicKeyHash)
    provide(keyStore)
  }

}

/**
  * Tezos-specific routes.
  * The mixed-in `DatabaseApiFiltering` trait provides the
  * instances of filtering execution implicitly needed by
  * several Api Operations, based on database querying
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class Tezos(implicit apiExecutionContext: ExecutionContext) extends LazyLogging with DatabaseApiFiltering
  with TezosEndpoints with akkahttp.server.Endpoints with akkahttp.server.JsonSchemaEntities with DataHelpers {


  /*
   * reuse the same context as the one for ApiOperations calls
   * as long as it doesn't create issues or performance degradation
   */
  override val asyncApiFiltersExecutionContext = apiExecutionContext

  private val blocksRoute = blocksEndpointV1.implementedByAsync {
    case (network, filter, apiKey) =>
      ApiOperations.fetchBlocks(filter)
  }

  private val blocksHeadRoute = blocksHeadEndpointV1.implementedByAsync {
    case (network, apiKey) =>
      ApiOperations.fetchLatestBlock()
  }

  private val blockByHashRoute = blockByHashEndpointV1.implementedByAsync {
    case (network, hash, apiKey) =>
      ApiOperations.fetchBlock(BlockHash(hash))
  }

  private val accountsRoute = accountsEndpointV1.implementedByAsync {
    case (network, filter, apiKey) =>
      ApiOperations.fetchAccounts(filter)
  }

  private val accountByIdRoute = accountByIdEndpointV1.implementedByAsync {
    case (network, accountId, apiKey) =>
      ApiOperations.fetchAccount(AccountId(accountId))
  }

  private val operationGroupsRoute = operationGroupsEndpointV1.implementedByAsync {
    case (network, filter, apiKey) =>
      ApiOperations.fetchOperationGroups(filter)
  }

  private val operationGroupsByIdRoute = operationGroupByIdEndpointV1.implementedByAsync {
    case (network, operationGroupId, apiKey) =>
      ApiOperations.fetchOperationGroup(operationGroupId)
  }

  private val avgFeesRoute = avgFeesEndpointV1.implementedByAsync {
    case (network, filter, apiKey) =>
      ApiOperations.fetchAverageFees(filter)
  }

  private val operationsRoute = operationsEndpointV1.implementedByAsync {
    case (network, filter, apiKey) =>
      ApiOperations.fetchOperations(filter)
  }

  val route: Route = concat(
    blocksRoute,
    blocksHeadRoute,
    blockByHashRoute,
    accountsRoute,
    accountByIdRoute,
    operationGroupsRoute,
    operationGroupsByIdRoute,
    avgFeesRoute,
    operationsRoute
  )
}
