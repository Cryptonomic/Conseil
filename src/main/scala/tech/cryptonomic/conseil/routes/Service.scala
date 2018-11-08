package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.ServiceOperations
import tech.cryptonomic.conseil.util.JsonUtil._
import tech.cryptonomic.conseil.util.RoutesUtil

import scala.concurrent.ExecutionContext

object Service {
  def apply(config: Config)(implicit apiExecutionContext: ExecutionContext): Service = new Service(config)
}

class Service(config: Config)(implicit apiExecutionContext: ExecutionContext) extends LazyLogging with RoutesUtil {
  val route: Route =
    get {
      pathPrefix("networks") {
        pathEnd {
          complete(toJson(ServiceOperations.getNetworks(config)))
        } ~ pathPrefix(Segment) { network =>
          pathPrefix("entities") {
            pathEnd {
              completeWithJson(ServiceOperations.getEntities(network))
            }
          }
        }
      }
    }
}
