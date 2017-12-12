package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try
import scalaj.http.{HttpOptions, HttpResponse}

/**
  * Tezos-specific functionality.
  */
object Tezos extends LazyLogging {

  val conf = ConfigFactory.load

  /**
    * Runs an RPC call against the configured Tezos node.
    * @param network  Which Tezos network to go against
    * @param path     RPC path to invoke
    * @return
    */
  def runTezosQuery(network: String, path: String): Try[String] = {
    Try{
      val tezos_hostname = conf.getString(s"platforms.tezos.${network}.node.hostname")
      val tezos_port = conf.getInt(s"platforms.tezos.${network}.node.port")
      val url = s"http://${tezos_hostname}:${tezos_port}/${path}"
      logger.info(s"Querying URL ${url} for platform Tezos and network ${network}")
      val response: HttpResponse[String] = scalaj.http.Http(url).postData("""{}""")
        .header("Content-Type", "application/json")
        .header("Charset", "UTF-8")
        .option(HttpOptions.readTimeout(100000))
        .option(HttpOptions.connTimeout(100000)).asString
      response.body
    }
  }

  val route = pathPrefix(Segment) { network =>
    pathPrefix("blocks") {
      get {
        pathEnd {
          complete(runTezosQuery(network, "blocks"))
        } ~ path("head") {
          complete(runTezosQuery(network, "blocks/head"))
        } ~ path(Segment) { blockId =>
          complete(runTezosQuery(network, s"blocks/${blockId}"))
        }
      }
    } ~ pathPrefix("accounts") {
      get {
        pathEnd {
          complete(runTezosQuery(network, "blocks/head/proto/context/contracts"))
        } ~ path(Segment) { accountId =>
          complete(runTezosQuery(network, s"blocks/head/proto/context/contracts/${accountId}"))
        }
      } ~ pathPrefix("operations") {
        get {
          pathEnd {
            complete(runTezosQuery(network, "blocks/head/proto/operations"))
          }
        }
      }
    }
  }
}
