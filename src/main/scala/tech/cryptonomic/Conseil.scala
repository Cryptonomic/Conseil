package tech.cryptonomic

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContextExecutor

object Conseil extends App with LazyLogging with EnableCORSDirectives {

  val conf = ConfigFactory.load
  val conseil_hostname = conf.getString("conseil.hostname")
  val conseil_port = conf.getInt("conseil.port")

  implicit val system: ActorSystem = ActorSystem("conseil-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val route = enableCORS {
      pathPrefix("tezos") {
        TezosRoutes.route
      }
  }

  val bindingFuture = Http().bindAndHandle(route, conseil_hostname, conseil_port)
  println(s"Bonjour..")
  while(true){}
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())

}
