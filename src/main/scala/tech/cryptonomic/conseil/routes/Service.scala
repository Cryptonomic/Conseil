package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import akka.http.scaladsl.marshalling.{PredefinedToEntityMarshallers, ToEntityMarshaller, ToResponseMarshallable, ToResponseMarshaller}
import akka.http.scaladsl.model.{MediaTypes, StatusCodes}
import akka.http.scaladsl.server.{Directive, Route, StandardRoute}
import akka.http.scaladsl.server.Directives._

import scala.collection.JavaConverters._
import tech.cryptonomic.conseil.util.JsonUtil._

import scala.concurrent.ExecutionContext
//Name: unique internal name that can be used to reference this object
//DisplayName: network name that may be visible to the user
//Platform: enum, currently having a single value - Tezos
//Network: network-specific subtype, in the case of Tezos, one of mainnet, zeronet, alphanet

class Service(config: Config)(implicit apiExecutionContext: ExecutionContext) extends LazyLogging {

  case class Network(name: String, platform: String, network: String)

  implicit private val jsonMarshaller: ToEntityMarshaller[JsonString] =
    PredefinedToEntityMarshallers.StringMarshaller
      .compose((_: JsonString).json)
      .wrap(MediaTypes.`application/json`)(identity _)


  val route: Route =
    get {
      pathPrefix("networks") {
        pathEnd {
          complete(toJson(getNetworks))
        }
      }
    }


  private def getNetworks = {
    config.getObject("platforms").asScala.flatMap {
      case (platform, v) =>
        v.atKey(platform).getObject(platform).asScala.map {
          case (network, _) =>
            Network(network, platform, network)
        }.toList
    }.toList
  }
}
