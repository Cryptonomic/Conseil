package tech.cryptonomic.conseil.tezos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util.Try

trait ShutdownComplete
object ShutdownComplete extends ShutdownComplete

/**
  * Interface into the Tezos blockchain.
  */
trait TezosRPCInterface {

  /**
    * Runs all needed RPC calls against the configured Tezos node using HTTP GET
    * @param network          Which Tezos network to go against
    * @param commands         RPC commands to invoke
    * @param concurrencyLevel How many request will be executed concurrently against the node
    * @return         Result of the RPC calls
    */
  def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]]

  /**
    * Runs an RPC call against the configured Tezos node using HTTP GET.
    * @param network  Which Tezos network to go against
    * @param command  RPC command to invoke
    * @return         Result of the RPC call
    */
  def runGetQuery(network: String, command: String): Try[String]

  /**
    * Runs an async RPC call against the configured Tezos node using HTTP GET.
    * @param network  Which Tezos network to go against
    * @param command  RPC command to invoke
    * @return         Result of the RPC call in a [[Future]]
    */
  def runAsyncGetQuery(network: String, command: String): Future[String]

  /**
    * Runs an RPC call against the configured Tezos node using HTTP POST.
    * @param network  Which Tezos network to go against
    * @param command  RPC command to invoke
    * @param payload  Optional JSON payload to post
    * @return         Result of the RPC call
    */
  def runPostQuery(network: String, command: String, payload: Option[String] = None): Try[String]

  /**
    * Runs an async RPC call against the configured Tezos node using HTTP POST.
    * @param network  Which Tezos network to go against
    * @param command  RPC command to invoke
    * @param payload  Optional JSON payload to post
    * @return         Result of the RPC call
    */
  def runAsyncPostQuery(network: String, command: String, payload: Option[String] = None): Future[String]

  /** Frees any resource that was eventually reserved */
  def shutdown(): Future[ShutdownComplete]= Future.successful(ShutdownComplete)
}

/**
  * Concrete implementation of the above.
  */
object TezosNodeInterface extends TezosRPCInterface with LazyLogging {

  private val conf = ConfigFactory.load
  private[tezos] val awaitTime = conf.getInt("dbAwaitTimeInSeconds").seconds
  private[tezos] val entityGetTimeout = conf.getInt("GET-ResponseEntityTimeoutInSeconds").seconds
  private[tezos] val entityPostTimeout = conf.getInt("POST-ResponseEntityTimeoutInSeconds").seconds

  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  override def shutdown(): Future[ShutdownComplete] = {
    Http().shutdownAllConnectionPools()
      .flatMap (_ => system.terminate())
      .map(_ => ShutdownComplete)(executor = scala.concurrent.ExecutionContext.Implicits.global)
  }

  private[this] def translateCommandToUrl(network: String, command: String): String = {
    val protocol = conf.getString(s"platforms.tezos.$network.node.protocol")
    val hostname = conf.getString(s"platforms.tezos.$network.node.hostname")
    val port = conf.getInt(s"platforms.tezos.$network.node.port")
    val pathPrefix = conf.getString(s"platforms.tezos.$network.node.pathPrefix")
    s"$protocol://$hostname:$port/${pathPrefix}chains/main/$command"
  }

  override def runGetQuery(network: String, command: String): Try[String] = {
    Try{
      val url = translateCommandToUrl(network, command)
      logger.debug(s"Querying URL $url for platform Tezos and network $network")
      val responseFuture: Future[HttpResponse] =
        Http(system).singleRequest(
          HttpRequest(
            HttpMethods.GET,
            url
          )
        )
      val response: HttpResponse = Await.result(responseFuture, awaitTime)
      val responseBodyFuture = response.entity.toStrict(entityGetTimeout).map(_.data.utf8String)
      val responseBody = Await.result(responseBodyFuture, awaitTime)
      logger.debug(s"Query result: $responseBody")
      responseBody
    }
  }

  override def runAsyncGetQuery(network: String, command: String): Future[String] = {
    val url = translateCommandToUrl(network, command)
    val request = HttpRequest(HttpMethods.GET, url)
    logger.debug("Async querying URL {} for platform Tezos and network {}", url, network)

    for {
      response <- Http(system).singleRequest(request)
      strict <- response.entity.toStrict(entityGetTimeout)
    } yield strict.data.utf8String

  }

  override def runPostQuery(network: String, command: String, payload: Option[String]= None): Try[String] = {
    Try{
      val url = translateCommandToUrl(network, command)
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
      val response: HttpResponse = Await.result(responseFuture, awaitTime)
      val responseBodyFuture = response.entity.toStrict(entityPostTimeout).map(_.data).map(_.utf8String)
      val responseBody = Await.result(responseBodyFuture, awaitTime)
      logger.debug(s"Query result: $responseBody")
      responseBody
    }
  }

  override def runAsyncPostQuery(network: String, command: String, payload: Option[String]= None): Future[String] = {
    val url = translateCommandToUrl(network, command)
    logger.debug(s"Async querying URL $url for platform Tezos and network $network with payload $payload")
    val postedData = payload.getOrElse("{}")
    val request = HttpRequest(
      HttpMethods.POST,
      url,
      entity = HttpEntity(ContentTypes.`application/json`, postedData.getBytes())
    )
    for {
      response <- Http(system).singleRequest(request)
      strict <- response.entity.toStrict(entityPostTimeout)
    } yield {
      val responseBody = strict.data.utf8String
      logger.debug("Query results: {}", responseBody)
      responseBody
    }
  }

  /** connection pool settings customized for streaming requests */
  private[this] val streamingRequestsConnectionPooling = ConnectionPoolSettings(
    conf
      .atPath("akka.tezos-streaming-client.connection-pool")
      .withFallback(ConfigFactory.defaultReference())
  )

  /** creates a connections pool based on the host network */
  private[this] def createHostPoolFlow(network: String) = {
    val host = conf.getString(s"platforms.tezos.$network.node.hostname")
    val port = conf.getInt(s"platforms.tezos.$network.node.port")
    val protocol = conf.getString(s"platforms.tezos.$network.node.protocol")
    if (protocol == "https")
      Http().cachedHostConnectionPoolHttps[String](
        host = host,
        port = port,
        settings = streamingRequestsConnectionPooling
      )
    else
      Http().cachedHostConnectionPool[String](
        host = host,
        port = port,
        settings = streamingRequestsConnectionPooling
      )
  }

  override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] = {
    val connections = createHostPoolFlow(network)
    val uris = Source(commands.map(translateCommandToUrl(network, _)))
    val toRequest = (url: String) => (HttpRequest(uri = Uri(url)), url)

    uris.map(toRequest)
      .via(connections)
      .mapAsync(concurrencyLevel) {
        case (tried, _) =>
          Future.fromTry(tried)
      }
      .mapAsync(1)(_.entity.toStrict(entityGetTimeout))
      .map(_.data.utf8String)
      .toMat(Sink.collection[String, List[String]])(Keep.right)
      .run()

  }
}
