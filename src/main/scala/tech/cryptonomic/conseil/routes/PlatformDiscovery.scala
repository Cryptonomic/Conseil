package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.JsonUtil._
import tech.cryptonomic.conseil.util.RoutesUtil

import scala.concurrent.ExecutionContext

object PlatformDiscovery {
  def apply(config: Config)(implicit apiExecutionContext: ExecutionContext): PlatformDiscovery = new PlatformDiscovery(config)
}

class PlatformDiscovery(config: Config)(implicit apiExecutionContext: ExecutionContext) extends LazyLogging with RoutesUtil {
  val route: Route =
    get {
      pathPrefix("networks") {
        pathEnd {
          complete(toJson(PlatformDiscoveryOperations.getNetworks(config)))
        } ~ pathPrefix(Segment) { network =>
          pathPrefix("entities") {
            pathEnd {
              completeWithJson(PlatformDiscoveryOperations.getEntities(network))
            } ~ pathPrefix(Segment) { entity =>
              pathPrefix("attributes") {
                pathEnd {
                  completeWithJson(PlatformDiscoveryOperations.tableAttributes(entity))
                } ~ pathPrefix(Segment) { attribute =>
                  pathEnd {
                    complete(
                      handleNoneAsNotFound(PlatformDiscoveryOperations.listAttributeValues(entity, attribute))
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
}
