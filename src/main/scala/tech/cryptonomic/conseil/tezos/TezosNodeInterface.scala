package tech.cryptonomic.conseil.tezos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import tech.cryptonomic.conseil.util.JsonUtil.JsonString
import tech.cryptonomic.conseil.config.ConseilConfig

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Try, Failure}
import scala.util.control.NoStackTrace

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
  def runPostQuery(network: String, command: String, payload: Option[JsonString] = None): Try[String]

  /**
    * Runs an async RPC call against the configured Tezos node using HTTP POST.
    * @param network  Which Tezos network to go against
    * @param command  RPC command to invoke
    * @param payload  Optional JSON payload to post
    * @return         Result of the RPC call
    */
  def runAsyncPostQuery(network: String, command: String, payload: Option[JsonString] = None): Future[String]

  /** Frees any resource that was eventually reserved */
  def shutdown(): Future[ShutdownComplete]= Future.successful(ShutdownComplete)
}

/**
  * Concrete implementation of the above.
  */
class TezosNodeInterface(config: ConseilConfig.TezosConfiguration)(implicit system: ActorSystem) extends TezosRPCInterface with LazyLogging {
  import config._

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  private[this] val rejectingCalls = new java.util.concurrent.atomic.AtomicBoolean(false)
  private[this] lazy val rejected = new Failure(new IllegalStateException("Tezos node requests will no longer be accepted.") with NoStackTrace)

  override def shutdown(): Future[ShutdownComplete] = {
    rejectingCalls.compareAndSet(false, true)
    Http(system).shutdownAllConnectionPools().map(_ => ShutdownComplete)
  }

  def withRejectionControl[T](call: => Try[T]): Try[T] =
    if (rejectingCalls.get) rejected else call

  def withRejectionControl[T](call: => Future[T]): Future[T] =
    if (rejectingCalls.get) Future.fromTry(rejected) else call

  private[this] def translateCommandToUrl(command: String): String =
    s"${nodeConfig.protocol}://${nodeConfig.hostname}:${nodeConfig.port}/${nodeConfig.pathPrefix}chains/main/$command"

  override def runGetQuery(network: String, command: String): Try[String] = withRejectionControl {
    Try{
      val url = translateCommandToUrl(command)
      logger.debug(s"Querying URL $url for platform Tezos and network $network")
      val responseFuture: Future[HttpResponse] =
        Http(system).singleRequest(
          HttpRequest(
            HttpMethods.GET,
            url
          )
        )
      val response: HttpResponse = Await.result(responseFuture, requestConfig.requestAwaitTime)
      val responseBodyFuture = response.entity.toStrict(requestConfig.getResponseEntityTimeout).map(_.data.utf8String)
      val responseBody = Await.result(responseBodyFuture, requestConfig.requestAwaitTime)
      logger.debug(s"Query result: $responseBody")
      responseBody
    }
  }

  override def runAsyncGetQuery(network: String, command: String): Future[String] = withRejectionControl {
    val url = translateCommandToUrl(command)
    val request = HttpRequest(HttpMethods.GET, url)
    logger.debug("Async querying URL {} for platform Tezos and network {}", url, config.network)

    for {
      response <- Http(system).singleRequest(request)
      strict <- response.entity.toStrict(requestConfig.getResponseEntityTimeout)
    } yield strict.data.utf8String

  }

  override def runPostQuery(network: String, command: String, payload: Option[JsonString]= None): Try[String] = withRejectionControl {
    Try {
      val url = translateCommandToUrl(command)
      logger.debug("Querying URL {} for platform Tezos and network {} with payload {}", url, config.network, payload)
      val postedData = payload.getOrElse(JsonString.emptyObject)
      val responseFuture: Future[HttpResponse] =
        Http(system).singleRequest(
          HttpRequest(
            HttpMethods.POST,
            url,
            entity = HttpEntity(ContentTypes.`application/json`, postedData.json.getBytes())
          )
        )
      val response: HttpResponse = Await.result(responseFuture, requestConfig.requestAwaitTime)
      val responseBodyFuture = response.entity.toStrict(requestConfig.postResponseEntityTimeout).map(_.data).map(_.utf8String)
      val responseBody = Await.result(responseBodyFuture, requestConfig.requestAwaitTime)
      logger.debug(s"Query result: $responseBody")
      responseBody

    }
  }

  override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonString]= None): Future[String] = withRejectionControl {
    val url = translateCommandToUrl(command)
    logger.debug("Async querying URL {} for platform Tezos and network {} with payload {}", url, config.network, payload)
    val postedData = payload.getOrElse(JsonString.emptyObject)
    val request = HttpRequest(
      HttpMethods.POST,
      url,
      entity = HttpEntity(ContentTypes.`application/json`, postedData.json.getBytes())
    )
    for {
      response <- Http(system).singleRequest(request)
      strict <- response.entity.toStrict(requestConfig.postResponseEntityTimeout)
    } yield {
      val responseBody = strict.data.utf8String
      logger.debug("Query results: {}", responseBody)
      responseBody
    }
  }

  /* Connection pool settings customized for streaming requests
   * This can't be swapped for a pureconfig definition because
   * it's an akka internal way of defining the pool options
   * and it's based on Lighbend Config class
   */
  private[this] val streamingRequestsConnectionPooling = ConnectionPoolSettings(
    ConfigFactory.load()
      .atPath("akka.tezos-streaming-client.connection-pool")
      .withFallback(ConfigFactory.defaultReference())
  )

  /* creates a connections pool based on the host network */
  private[this] val connectionPool = {
    if (nodeConfig.protocol == "https")
      Http(system).cachedHostConnectionPoolHttps[String](
        host = nodeConfig.hostname,
        port = nodeConfig.port,
        settings = streamingRequestsConnectionPooling
      )
    else
      Http(system).cachedHostConnectionPool[String](
        host = nodeConfig.hostname,
        port = nodeConfig.port,
        settings = streamingRequestsConnectionPooling
      )
  }

  override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] = withRejectionControl {
    val uris = Source(commands.map(translateCommandToUrl))
    val toRequest = (url: String) => (HttpRequest(uri = Uri(url)), url)

    uris.map(toRequest)
      .via(connectionPool)
      .mapAsync(concurrencyLevel) {
        case (tried, _) =>
          Future.fromTry(tried)
      }
      .mapAsync(1)(_.entity.toStrict(requestConfig.getResponseEntityTimeout))
      .map(_.data.utf8String)
      .toMat(Sink.collection[String, List[String]])(Keep.right)
      .run()

  }
}
