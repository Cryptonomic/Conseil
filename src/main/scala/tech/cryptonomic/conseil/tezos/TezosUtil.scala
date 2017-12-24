package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.util.JsonUtil.fromJson

import scala.util.{Failure, Success, Try}
import scalaj.http.{HttpOptions, HttpResponse}

object TezosUtil extends LazyLogging{

  val conf = ConfigFactory.load

  /**
    * Runs an RPC call against the configured Tezos node.
    * @param network  Which Tezos network to go against
    * @param path     RPC path to invoke
    * @return
    */
  def runQuery(network: String, path: String): Try[String] = {
    Try{
      val tezos_hostname = conf.getString(s"platforms.tezos.${network}.node.hostname")
      val tezos_port = conf.getInt(s"platforms.tezos.${network}.node.port")
      val url = s"http://${tezos_hostname}:${tezos_port}/tezos/${network}/${path}"
      logger.info(s"Querying URL ${url} for platform Tezos and network ${network}")
      val response: HttpResponse[String] = scalaj.http.Http(url).postData("""{}""")
        .header("Content-Type", "application/json")
        .header("Charset", "UTF-8")
        .option(HttpOptions.readTimeout(100000))
        .option(HttpOptions.connTimeout(100000)).asString
      response.body
    }
  }

  def getBlock(network: String, hash: String): Try[Types.Block] = {
    val result = runQuery(network, s"blocks/${hash}")
    result.flatMap(jsonEncodedBlock =>
      Try(fromJson[Types.Block](jsonEncodedBlock))
    )
  }

  def getBlockHead(network: String): Try[Types.Block]= {
    getBlock(network, "head")
  }

}
