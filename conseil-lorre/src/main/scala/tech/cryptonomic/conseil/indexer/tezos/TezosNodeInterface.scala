package tech.cryptonomic.conseil.indexer.tezos

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.common.util.JsonUtil.JsonString
import tech.cryptonomic.conseil.common.config.{HttpStreamingConfiguration, NetworkCallsConfiguration}
import tech.cryptonomic.conseil.common.config.Platforms.TezosConfiguration
import tech.cryptonomic.conseil.indexer.LorreIndexer.ShutdownComplete

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Try}
import scala.util.control.NoStackTrace

/**
  * Interface into the Tezos blockchain.
  */
private[tezos] trait TezosRPCInterface {

  /**
    * Runs all needed RPC calls against the configured Tezos node using HTTP GET
    * @param network  Which Tezos network to go against
    * @param ids  correlation ids for each request to send
    * @param mapToCommand  extract a tezos command (uri fragment) from the id
    * @param concurrencyLevel the concurrency in processing the responses
    * @tparam ID a type that will be used to correlate each request with the response
    * @return pairing of the correlation id with the string http response content
    */
  def runBatchedGetQuery[ID](
      network: String,
      ids: List[ID],
      mapToCommand: ID => String,
      concurrencyLevel: Int
  ): Future[List[(ID, String)]]

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
    * @return         Result of the RPC call in a `Future`
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
  def shutdown(): Future[ShutdownComplete] = Future.successful(ShutdownComplete)
}

/**
  * Concrete implementation of the above.
  */
private[tezos] class TezosNodeInterface(
    config: TezosConfiguration,
    requestConfig: NetworkCallsConfiguration,
    streamingConfig: HttpStreamingConfiguration
)(implicit system: ActorSystem)
    extends TezosRPCInterface
    with LazyLogging {
  import config.nodeConfig

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  private[this] val rejectingCalls = new java.util.concurrent.atomic.AtomicBoolean(false)
  private[this] lazy val rejected = Failure(
    new IllegalStateException("Tezos node requests will no longer be accepted.") with NoStackTrace
  )

  override def shutdown(): Future[ShutdownComplete] = {
    rejectingCalls.compareAndSet(false, true)
    Http(system).shutdownAllConnectionPools().map(_ => ShutdownComplete)
  }

  def withRejectionControl[T](call: => Try[T]): Try[T] =
    if (rejectingCalls.get) rejected else call

  def withRejectionControl[T](call: => Future[T]): Future[T] =
    if (rejectingCalls.get) Future.fromTry(rejected) else call

  def withRejectionControl[T](call: => Source[T, NotUsed]): Source[T, NotUsed] =
    if (rejectingCalls.get) Source.empty[T] else call

  private[this] def translateCommandToUrl(command: String): String =
    s"${nodeConfig.protocol}://${nodeConfig.hostname}:${nodeConfig.port}/${nodeConfig.pathPrefix}chains/${nodeConfig.chainEnv}/$command"

  override def runGetQuery(network: String, command: String): Try[String] = withRejectionControl {
    Try {
      val url = translateCommandToUrl(command)
      logger.debug("Querying URL {} for platform Tezos and network {}", url, config.network)
      val responseFuture: Future[HttpResponse] =
        Http(system).singleRequest(
          HttpRequest(
            HttpMethods.GET,
            url
          )
        )
      val response: HttpResponse = Await.result(responseFuture, requestConfig.requestAwaitTime)
      val responseBodyFuture = response.entity.toStrict(requestConfig.GETResponseEntityTimeout).map(_.data.utf8String)
      val responseBody = Await.result(responseBodyFuture, requestConfig.requestAwaitTime)
      logger.debug(s"Query result: $responseBody")
      JsonString sanitize responseBody
    }
  }

  override def runAsyncGetQuery(network: String, command: String): Future[String] = withRejectionControl {
    val url = translateCommandToUrl(command)
    val request = HttpRequest(HttpMethods.GET, url)
    logger.debug("Async querying URL {} for platform Tezos and network {}", url, config.network)

    for {
      response <- Http(system).singleRequest(request)
      strict <- response.entity.toStrict(requestConfig.GETResponseEntityTimeout)
    } yield JsonString sanitize strict.data.utf8String

  }

  override def runPostQuery(network: String, command: String, payload: Option[JsonString] = None): Try[String] =
    withRejectionControl {
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
        val responseBodyFuture =
          response.entity.toStrict(requestConfig.POSTResponseEntityTimeout).map(_.data).map(_.utf8String)
        val responseBody = Await.result(responseBodyFuture, requestConfig.requestAwaitTime)
        logger.debug(s"Query result: $responseBody")
        JsonString sanitize responseBody

      }
    }

  override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonString] = None): Future[String] =
    withRejectionControl {
      val url = translateCommandToUrl(command)
      logger.debug(
        "Async querying URL {} for platform Tezos and network {} with payload {}",
        url,
        config.network,
        payload
      )
      val postedData = payload.getOrElse(JsonString.emptyObject)
      val request = HttpRequest(
        HttpMethods.POST,
        url,
        entity = HttpEntity(ContentTypes.`application/json`, postedData.json.getBytes())
      )
      for {
        response <- Http(system).singleRequest(request)
        strict <- response.entity.toStrict(requestConfig.POSTResponseEntityTimeout)
      } yield {
        val responseBody = strict.data.utf8String
        logger.debug("Query results: {}", responseBody)
        JsonString sanitize responseBody
      }
    }

  /* Connection pool settings customized for streaming requests */
  protected[tezos] val streamingRequestsConnectionPooling: ConnectionPoolSettings = ConnectionPoolSettings(
    streamingConfig.pool
  )

  /* creates a connections pool based on the host network */
  private[this] def getHostPoolFlow[T] =
    if (nodeConfig.protocol == "https")
      Http(system).cachedHostConnectionPoolHttps[T](
        host = nodeConfig.hostname,
        port = nodeConfig.port,
        settings = streamingRequestsConnectionPooling
      )
    else
      Http(system).cachedHostConnectionPool[T](
        host = nodeConfig.hostname,
        port = nodeConfig.port,
        settings = streamingRequestsConnectionPooling
      )

  /**
    * Creates a stream that will produce http response content for the
    * list of commands.
    *
    * @param ids  correlation ids for each request to send
    * @param mapToCommand  extract a tezos command (uri fragment) from the id
    * @param concurrencyLevel the concurrency in processing the responses
    * @tparam CID a type that will be used to correlate each request with the response
    * @return A stream source whose elements will be the response string, tupled with the correlation id,
    *         used to match with the corresponding request
    */
  private[this] def streamedGetQuery[CID](
      ids: List[CID],
      mapToCommand: CID => String,
      concurrencyLevel: Int
  ): Source[(CID, String), akka.NotUsed] = withRejectionControl {

    val convertIdToUrl = mapToCommand andThen translateCommandToUrl

    //we need to thread the id all through the streaming http stages
    val uris = Source(ids.map(id => (convertIdToUrl(id), id)))

    val toRequest: ((String, CID)) => (HttpRequest, CID) = {
      case (url, id) =>
        logger.debug("Will query: " + url)
        (HttpRequest(uri = Uri(url)), id)
    }

    uris
      .map(toRequest)
      .via(getHostPoolFlow)
      .mapAsyncUnordered(concurrencyLevel) {
        case (tried, id) =>
          Future
            .fromTry(tried.map(_.entity.toStrict(requestConfig.GETResponseEntityTimeout)))
            .flatten
            .map(entity => (entity, id))
      }
      .map { case (content: HttpEntity.Strict, id) => (id, JsonString sanitize content.data.utf8String) }
  }

  override def runBatchedGetQuery[CID](
      network: String,
      ids: List[CID],
      mapToCommand: CID => String,
      concurrencyLevel: Int
  ): Future[List[(CID, String)]] = {
    val batchId = java.util.UUID.randomUUID()
    logger.debug("{} - New batched GET call for {} requests", batchId, ids.size)

    streamedGetQuery(ids, mapToCommand, concurrencyLevel)
      .runFold(List.empty[(CID, String)])(_ :+ _)
      .andThen {
        case _ => logger.debug("{} - Batch completed", batchId)
      }
  }

}
