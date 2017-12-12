package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.directives.EnableCORSDirectives
import tech.cryptonomic.conseil.routes.Tezos
import tech.cryptonomic.conseil.util.SecurityUtil

import akka.http.scaladsl.model.StatusCodes._

import scala.concurrent.ExecutionContextExecutor

object Conseil extends App with LazyLogging with EnableCORSDirectives {

  val validateApiKey = headerValueByName("apikey").tflatMap[Tuple1[String]]{
    case Tuple1(apiKey) =>
      SecurityUtil.validateApiKey(apiKey) match {
        case true => provide (apiKey)
        case false => complete((Unauthorized, "Incorrect API key"))
      }
  }

  val conf = ConfigFactory.load
  val conseil_hostname = conf.getString("conseil.hostname")
  val conseil_port = conf.getInt("conseil.port")

  implicit val system: ActorSystem = ActorSystem("conseil-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val route = enableCORS {
    validateApiKey { apiKey =>
      pathPrefix("tezos") {
        Tezos.route
      }
    }
  }

  val bindingFuture = Http().bindAndHandle(route, conseil_hostname, conseil_port)
  println(s"Bonjour..")
  while(true){}
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())

}
