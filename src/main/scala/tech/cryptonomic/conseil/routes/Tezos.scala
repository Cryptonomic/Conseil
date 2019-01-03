package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Route}
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.tezos._
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.RouteHandling

import scala.concurrent.ExecutionContext

/** Provides useful route and directive definitions */
object Tezos {

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
  ).tflatMap {
    case (publicKey, privateKey, publicKeyHash) =>
      val keyStore = KeyStore(publicKey = publicKey, privateKey = privateKey, publicKeyHash = publicKeyHash)
      provide(keyStore)
  }

  def apply(apiNetworkOperations: ApiNetworkOperations)(implicit apiExecutionContext: ExecutionContext) =
    new Tezos(apiNetworkOperations)

}

/**
  * Tezos-specific routes.
  * The mixed-in `DatabaseApiFiltering` trait provides the
  * instances of filtering execution implicitly needed by
  * several Api Operations, based on database querying
  *
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class Tezos(apiNetworkOperations: ApiNetworkOperations)
  (implicit apiExecutionContext: ExecutionContext) extends LazyLogging with RouteHandling {

  import Tezos._

  /*
   * reuse the same context as the one for ApiOperations calls
   * as long as it doesn't create issues or performance degradation
   */
  val asyncApiFiltersExecutionContext = apiExecutionContext

  /** expose filtered results through rest endpoints */
  val route: Route = pathPrefix(Segment) { network =>
    apiNetworkOperations.getApiOperations("tezos", network) { apiOperations =>
      apiNetworkOperations.getApiFiltering("tezos", network) { apiFiltering =>
        get {
          gatherConseilFilter { filter =>
            import apiFiltering._
            validate(filter.limit.forall(_ <= 10000), "Cannot ask for more than 10000 entries") {
              pathPrefix("blocks") {
                pathEnd {
                  completeWithJson(apiOperations.fetchBlocks(filter))
                } ~ path("head") {
                  completeWithJson(apiOperations.fetchLatestBlock())
                } ~ path(Segment).as(BlockHash) { blockId =>
                  complete(
                    handleNoneAsNotFound(apiOperations.fetchBlock(blockId))
                  )
                }
              } ~ pathPrefix("accounts") {
                pathEnd {
                  completeWithJson(apiOperations.fetchAccounts(filter))
                } ~ path(Segment).as(AccountId) { accountId =>
                  complete(
                    handleNoneAsNotFound(apiOperations.fetchAccount(accountId))
                  )
                }
              } ~ pathPrefix("operation_groups") {
                pathEnd {
                  completeWithJson(apiOperations.fetchOperationGroups(filter))
                } ~ path(Segment) { operationGroupId =>
                  complete(
                    handleNoneAsNotFound(apiOperations.fetchOperationGroup(operationGroupId))
                  )
                }
              } ~ pathPrefix("operations") {
                path("avgFees") {
                  complete(
                    handleNoneAsNotFound(apiOperations.fetchAverageFees(filter))
                  )
                } ~ pathEnd {
                  completeWithJson(apiOperations.fetchOperations(filter))
                }
              }
            }
          }
        }
      }
    }
  }
}