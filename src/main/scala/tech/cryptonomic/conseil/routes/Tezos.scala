package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.marshalling.{PredefinedToEntityMarshallers, ToEntityMarshaller}
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.server.{Directive, Route}
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos._
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter
import tech.cryptonomic.conseil.tezos.TezosTypes.{BlockHash, AccountId}
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.util.JsonUtil
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.JsonUtil

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
  ).as(Filter.readParams)

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
  * The mixed-in [[DatabaseApiFiltering]] trait provides the
  * instances of filtering execution implicitly needed by
  * several Api Operations, based on database querying
  * @param apiExecutionContext is used to call the async operations exposed by the api service
  */
class Tezos(implicit apiExecutionContext: ExecutionContext) extends LazyLogging with DatabaseApiFiltering {

  import Tezos._

  /* reuse the same context as the one for ApiOperations calls
   * as long as it doesn't create issues or performance degradation
   */
  override val asyncApiFiltersExecutionContext = apiExecutionContext

  //this automatically accepts any type `T` as content for calling [[RequestContext.complete]]
  //converts to json string via JsonUtil adding the correct content-type to the response entity
  implicit def jsonStringMarshaller[T]: ToEntityMarshaller[T] =
    PredefinedToEntityMarshallers.StringMarshaller
      .compose(JsonUtil.toJson[T])
      .wrap(MediaTypes.`application/json`)(identity)

  /** expose filtered results through rest endpoints */
  val route: Route = pathPrefix(Segment) { network =>
    get {
      gatherConseilFilter{ filter =>
        validate(filter.limit.forall(_ <= 10000), s"Cannot ask for more than 10000 entries") {
          pathPrefix("blocks") {
            pathEnd {
              complete(ApiOperations.fetchBlocks(filter))
            } ~ path("head") {
                complete(ApiOperations.fetchLatestBlock())
            } ~ path(Segment).as(BlockHash) { blockId =>
                complete(ApiOperations.fetchBlock(blockId))
            }
          } ~ pathPrefix("accounts") {
            pathEnd {
              complete(ApiOperations.fetchAccounts(filter))
            } ~ path(Segment).as(AccountId) { accountId =>
              complete(ApiOperations.fetchAccount(accountId))
            }
          } ~ pathPrefix("operation_groups") {
            pathEnd {
              complete(ApiOperations.fetchOperationGroups(filter))
            } ~ path(Segment) { operationGroupId =>
              complete(ApiOperations.fetchOperationGroup(operationGroupId))
            }
          } ~ pathPrefix("operations") {
            path("avgFees") {
                complete(ApiOperations.fetchAverageFees(filter))
            } ~ pathEnd {
                complete(ApiOperations.fetchOperations(filter))
            }
          }
        }

      }
    }
  }
}