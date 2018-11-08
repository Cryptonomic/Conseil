package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.marshalling.{PredefinedToEntityMarshallers, ToEntityMarshaller}
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.ServiceOperations
import tech.cryptonomic.conseil.util.JsonUtil._

import scala.concurrent.{ExecutionContext, Future}

object Service {
  def apply(config: Config)(implicit apiExecutionContext: ExecutionContext): Service = new Service(config)
}

class Service(config: Config)(implicit apiExecutionContext: ExecutionContext) extends LazyLogging {
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

  implicit private val jsonMarshaller: ToEntityMarshaller[JsonString] =
    PredefinedToEntityMarshallers.StringMarshaller
      .compose((_: JsonString).json)
      .wrap(MediaTypes.`application/json`)(identity _)

  private def completeWithJson[T](futureValue: Future[T]): StandardRoute =
    complete(futureValue.map(toJson[T]))

}
