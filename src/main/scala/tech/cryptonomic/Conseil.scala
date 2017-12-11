package tech.cryptonomic

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.Try
import scalaj.http._

object Conseil extends App with LazyLogging with EnableCORSDirectives {

  val conf = ConfigFactory.load
  val conseil_hostname = conf.getString("conseil.hostname")
  val conseil_port = conf.getInt("conseil.port")
  val tezos_hostname = conf.getString("tezos.node.hostname")
  val tezos_port = conf.getInt("tezos.node.port")

  implicit val system: ActorSystem = ActorSystem("conseil-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  def runTezosQuery(path: String): Try[String] = {
    Try{
      val url = s"http://${tezos_hostname}:${tezos_port}/${path}"
      logger.info(s"Querying URL: ${url}")
      val response: HttpResponse[String] = scalaj.http.Http(url).postData("""{}""")
        .header("Content-Type", "application/json")
        .header("Charset", "UTF-8")
        .option(HttpOptions.readTimeout(100000))
        .option(HttpOptions.connTimeout(100000)).asString
      response.body
    }
  }

  val route = enableCORS {
      pathPrefix("blocks") {
        get {
          pathEnd {
            complete(runTezosQuery("blocks"))
          } ~ path("head") {
            complete(runTezosQuery("blocks/head"))
          } ~ path(Segment) { blockId =>
            complete(runTezosQuery(s"blocks/${blockId}"))
          }
        }
      } ~ pathPrefix("accounts") {
        get {
          pathEnd {
            complete(runTezosQuery("blocks/head/proto/context/contracts"))
          } ~ path(Segment) { accountId =>
            complete(runTezosQuery(s"blocks/head/proto/context/contracts/${accountId}"))
          }
        } ~ pathPrefix("operations") {
          get {
            pathEnd {
              complete(runTezosQuery("blocks/head/proto/operations"))
            }
          }
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
