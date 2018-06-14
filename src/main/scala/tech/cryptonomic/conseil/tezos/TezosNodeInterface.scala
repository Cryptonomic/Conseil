package tech.cryptonomic.conseil.tezos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.Try

/**
  * Interface into the Tezos blockchain.
  */
trait TezosRPCInterface {
  def runGetQuery(network: String, command: String, payload: Option[String] = None): Try[String]
  def runPostQuery(network: String, command: String, payload: Option[String] = None): Try[String]
}

/**
  * Concrete implementation of the above.
  */
object TezosNodeInterface extends TezosRPCInterface with LazyLogging {

  private val conf = ConfigFactory.load

  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  /**
    * Runs an RPC call against the configured Tezos node.
    * @param network  Which Tezos network to go against
    * @param command  RPC command to invoke
    * @param payload  Optional JSON pyaload to post
    * @return         Result of the RPC call
    */
  def runGetQuery(network: String, command: String, payload: Option[String]= None): Try[String] = {
    Try{
      val hostname = conf.getString(s"platforms.tezos.$network.node.hostname")
      val port = conf.getInt(s"platforms.tezos.$network.node.port")
      val pathPrefix = conf.getString(s"platforms.tezos.$network.node.pathPrefix")
      val url = s"http://$hostname:$port/${pathPrefix}chains/main/$command"
      logger.debug(s"Querying URL $url for platform Tezos and network $network with payload $payload")
      val postedData = payload match {
        case None => """{}"""
        case Some(str) => str
      }

      val responseFuture: Future[HttpResponse] =
        Http(system).singleRequest(
          HttpRequest(
            HttpMethods.GET,
            url
          )
        )
      val response: HttpResponse = Await.result(responseFuture, Duration.Inf)
      val responseBodyFuture = response.entity.toStrict(5.second).map(_.data).map(_.utf8String)
      val responseBody = Await.result(responseBodyFuture, Duration.Inf)
      logger.debug(s"Query result: ${responseBody}")
      responseBody
    }
  }

  /**
    * Runs an RPC call against the configured Tezos node.
    * @param network  Which Tezos network to go against
    * @param command  RPC command to invoke
    * @param payload  Optional JSON pyaload to post
    * @return         Result of the RPC call
    */
  def runPostQuery(network: String, command: String, payload: Option[String]= None): Try[String] = {
    Try{
      val hostname = conf.getString(s"platforms.tezos.$network.node.hostname")
      val port = conf.getInt(s"platforms.tezos.$network.node.port")
      val pathPrefix = conf.getString(s"platforms.tezos.$network.node.pathPrefix")
      val url = s"http://$hostname:$port/${pathPrefix}chains/main/$command"
      logger.debug(s"Querying URL $url for platform Tezos and network $network with payload $payload")
      val postedData = payload match {
        case None => """{}"""
        case Some(str) => str
      }

      val responseFuture: Future[HttpResponse] =
        Http(system).singleRequest(
          HttpRequest(
            HttpMethods.POST,
            url,
            entity = HttpEntity(ContentTypes.`application/json`, postedData.getBytes())
          )
        )
      val response: HttpResponse = Await.result(responseFuture, Duration.Inf)
      val responseBodyFuture = response.entity.toStrict(1.second).map(_.data).map(_.utf8String)
      val responseBody = Await.result(responseBodyFuture, Duration.Inf)
      logger.debug(s"Query result: ${responseBody}")
      responseBody
    }
  }

}
