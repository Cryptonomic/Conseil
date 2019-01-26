package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.generic.chain.DataPlatform
import tech.cryptonomic.conseil.generic.chain.DataTypes.ApiQuery
import tech.cryptonomic.conseil.tezos.ApiOperations
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.util.RouteHandling

import scala.concurrent.ExecutionContext

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
  extends LazyLogging with RouteHandling with DatabaseApiFiltering with JacksonSupport {

  import Tezos._

  /*
   * reuse the same context as the one for ApiOperations calls
   * as long as it doesn't create issues or performance degradation
   */
  override val asyncApiFiltersExecutionContext: ExecutionContext = apiExecutionContext

  /** Route for the POST query */
  val postRoute: Route =
    post {
      commonRoute { (platform, network) =>
        pathPrefix(Segment) { ent =>
          validateEntity(ent) {
            entity(as[ApiQuery]) { apiQuery: ApiQuery =>
              validateQueryOrBadRequest(ent, apiQuery) { validatedQuery =>
                completeWithJsonOrNotFound(queryProtocolPlatform.queryWithPredicates(platform, ent, validatedQuery))
              }
            }
          }
        }
      }
    }

  /** Route for the GET query with query parameters filtering */
  val getRoute: Route =
    get {
      commonRoute {
        (platform, network) =>
          gatherConseilFilter { filter =>
            validate(filter.limit.forall(_ <= 10000), "Cannot ask for more than 10000 entries") {
              pathPrefix("blocks") {
                pathEnd {
                  completeWithJsonOrNotFound(
                    queryProtocolPlatform.queryWithPredicates(platform, "blocks", filter.toQuery)
                  )
                } ~ path("head") {
                  completeWithJson(
                    ApiOperations.fetchLatestBlock()
                  )
                } ~ path(Segment).as(BlockHash) { blockId =>
                  complete(
                    handleNoneAsNotFound(
                      ApiOperations.fetchBlock(blockId)
                    )
                  )
                }
              } ~ pathPrefix("accounts") {
                pathEnd {
                  completeWithJsonOrNotFound(
                    queryProtocolPlatform.queryWithPredicates(platform, "accounts", filter.toQuery)
                  )
                } ~ path(Segment).as(AccountId) { accountId =>
                  complete(
                    handleNoneAsNotFound(
                      ApiOperations.fetchAccount(accountId)
                    )
                  )
                }
              } ~ pathPrefix("operation_groups") {
                pathEnd {
                  completeWithJsonOrNotFound(
                    queryProtocolPlatform.queryWithPredicates(platform, "operation_groups", filter.toQuery)
                  )
                } ~ path(Segment) { operationGroupId =>
                  complete(
                    handleNoneAsNotFound(ApiOperations.fetchOperationGroup(operationGroupId))
                  )
                }
              } ~ pathPrefix("operations") {
                path("avgFees") {
                  complete(
                    handleNoneAsNotFound(ApiOperations.fetchAverageFees(filter))
                  )
                } ~ pathEnd {
                  completeWithJsonOrNotFound(
                    queryProtocolPlatform.queryWithPredicates(platform, "operations", filter.toQuery)
                  )
                }
              }
            }
          }

      }
    }

  /** common route builder with platform and network validation */
  private def commonRoute(routeBuilder: (String, String) => Route): Route =
    pathPrefix(Segment) { platform =>
      pathPrefix(Segment) { network =>
        validatePlatformAndNetwork(config, platform, network) {
            routeBuilder(platform, network)
        }
      }
    }

}
