package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.generic.chain.ApiNetworkOperations
import tech.cryptonomic.conseil.generic.chain.DataTypes.Query
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.util.RouteHandling

import scala.concurrent.ExecutionContext

/** Companion object providing apply implementation */
object Data {
  def apply(config: PlatformsConfiguration, apiNetworkOperations: ApiNetworkOperations)(implicit ec: ExecutionContext): Data = new Data(config, apiNetworkOperations)
}

/**
  * Platform discovery routes.
  *
  * @param apiExecutionContext   is used to call the async operations exposed by the api service
  * @param apiNetworkOperations  apiNetworkOperations object which checks if platform exists and executes query
  */
class Data(config: PlatformsConfiguration, apiNetworkOperations: ApiNetworkOperations)(implicit apiExecutionContext: ExecutionContext)
  extends LazyLogging with RouteHandling with JacksonSupport {

  import Tezos._
  import apiNetworkOperations._


  /** Route for the POST query */
  val postRoute: Route =
    post {
      commonRoute { (platform, network) =>
        getApiOperations(platform, network) { apiOperations =>
          pathPrefix(Segment) { ent =>
            validateEntity(ent) {
              entity(as[Query]) { query: Query =>
                validateQueryOrBadRequest(query) { validatedQuery =>
                  completeWithJson(apiOperations.queryWithPredicates(ent, validatedQuery))
                }
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
          getApiOperations(platform, network) { apiOperations =>
            getApiFiltering("tezos", network) { apiFiltering =>
              import apiFiltering._
            gatherConseilFilter { filter =>
              validate(filter.limit.forall(_ <= 10000), "Cannot ask for more than 10000 entries") {
                pathPrefix("blocks") {
                  pathEnd {
                    completeWithJson(
                      apiOperations.queryWithPredicates("blocks", filter.toQuery)
                    )
                  } ~ path("head") {
                    completeWithJson(
                      apiOperations.fetchLatestBlock()
                    )
                  } ~ path(Segment).as(BlockHash) { blockId =>
                    complete(
                      handleNoneAsNotFound(
                        apiOperations.fetchBlock(blockId)
                      )
                    )
                  }
                } ~ pathPrefix("accounts") {
                  pathEnd {
                    completeWithJson(
                      apiOperations.queryWithPredicates("accounts", filter.toQuery)
                    )
                  } ~ path(Segment).as(AccountId) { accountId =>
                    complete(
                      handleNoneAsNotFound(
                        apiOperations.fetchAccount(accountId)
                      )
                    )
                  }
                } ~ pathPrefix("operation_groups") {
                  pathEnd {
                    completeWithJson(
                      apiOperations.queryWithPredicates( "operation_groups", filter.toQuery)
                    )
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
                    completeWithJson(
                      apiOperations.queryWithPredicates("operations", filter.toQuery)
                    )
                  }
                }
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
