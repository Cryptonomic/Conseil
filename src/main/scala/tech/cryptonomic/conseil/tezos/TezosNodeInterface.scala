package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try
import scalaj.http.{HttpOptions, HttpResponse}

/**
  * Interface into the Tezos blockchain.
  */
trait TezosRPCInterface {
  def runQuery(network: String, command: String): Try[String]
}

/**
  * Concrete implementation of the above.
  */
object TezosNodeInterface extends TezosRPCInterface with LazyLogging {

  private val conf = ConfigFactory.load

  /**
    * Runs an RPC call against the configured Tezos node.
    * @param network  Which Tezos network to go against
    * @param command  RPC command to invoke
    * @return         Result of the RPC call
    */
  def runQuery(network: String, command: String): Try[String] = {
    Try{
      val hostname = conf.getString(s"platforms.tezos.$network.node.hostname")
      val port = conf.getInt(s"platforms.tezos.$network.node.port")
      val pathPrefix = conf.getString(s"platforms.tezos.$network.node.pathPrefix")
      val url = s"http://$hostname:$port/$pathPrefix$command"
      logger.info(s"Querying URL $url for platform Tezos and network $network")
      val response: HttpResponse[String] = scalaj.http.Http(url).postData("""{}""")
        .header("Content-Type", "application/json")
        .header("Charset", "UTF-8")
        .option(HttpOptions.readTimeout(100000))
        .option(HttpOptions.connTimeout(100000)).asString
      response.body
    }
  }

}
