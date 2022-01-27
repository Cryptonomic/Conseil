package tech.cryptonomic.conseil.indexer.tezos

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{ActorMaterializer, Attributes, Materializer}
import akka.stream.Attributes.LogLevels
import akka.stream.scaladsl.{Flow, Source}
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.util.JsonUtil.JsonString
import tech.cryptonomic.conseil.common.config.Platforms.TezosConfiguration
import tech.cryptonomic.conseil.indexer.LorreIndexer.ShutdownComplete
import tech.cryptonomic.conseil.indexer.config.{HttpStreamingConfiguration, NetworkCallsConfiguration}

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Try}
import scala.util.control.NoStackTrace
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogger
import scribe._
import java.{util => ju}

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
    with ConseilLogSupport {
  import config.node

  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  private[this] val rejectingCalls = new java.util.concurrent.atomic.AtomicBoolean(false)
  private[this] lazy val rejected = Failure(
    new IllegalStateException("Tezos node requests will no longer be accepted.") with NoStackTrace
  )

  final val traceLoggingGetCategory = "tech.cryptonomic.conseil.indexer.tezos.node-rpc.get"
  final val traceLoggingPostCategory = "tech.cryptonomic.conseil.indexer.tezos.node-rpc.post"
  final val traceLoggingBatchCategory = "tech.cryptonomic.conseil.indexer.tezos.node-rpc.batch"

  private lazy val asyncGetLogger =
    ConseilLogger(traceLoggingGetCategory, Some(Level.Debug))
  private lazy val asyncPostLogger =
    ConseilLogger(traceLoggingPostCategory, Some(Level.Debug))
  /* Defines the custom akka logging category to emit request-response traces on each stream element */
  private lazy val batchedRpcTraceLogger: LoggingAdapter =
    Logging(system.eventStream, traceLoggingBatchCategory)

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
    s"${node.protocol}://${node.hostname}:${node.port}/${node.pathPrefix}chains/${node.chainEnv}/$command"

  override def runGetQuery(network: String, command: String): Try[String] = withRejectionControl {
    Try {
      val url = translateCommandToUrl(command)
      logger.debug(s"Querying URL $url for platform Tezos and network ${config.network}")
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

    val cid = ju.UUID.randomUUID().toString()
    if (node.traceCalls) {
      asyncGetLogger.debug(s"Tezos node rpc request. Corr-Id $cid: ${request.uri}")
    }

    for {
      response <- Http(system).singleRequest(request)
      strict <- response.entity.toStrict(requestConfig.GETResponseEntityTimeout)
    } yield {
      val responseBody = strict.data.utf8String
      if (node.traceCalls) {
        asyncGetLogger.debug(s"Tezos node response. Corr-Id $cid: $response")
        asyncGetLogger.debug(s"Tezos node rpc json payload. Corr-Id $cid: $responseBody")
      }
      JsonString sanitize responseBody
    }

  }

  override def runPostQuery(network: String, command: String, payload: Option[JsonString] = None): Try[String] =
    withRejectionControl {
      Try {
        val url = translateCommandToUrl(command)
        logger.debug(
          s"Querying URL $url for platform Tezos and network ${config.network} with payload ${payload.getOrElse("missing")}"
        )
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
      val postedData = payload.getOrElse(JsonString.emptyObject)
      val request = HttpRequest(
        HttpMethods.POST,
        url,
        entity = HttpEntity(ContentTypes.`application/json`, postedData.json.getBytes())
      )
      val cid = ju.UUID.randomUUID().toString()
      if (node.traceCalls) {
        asyncPostLogger.debug(s"Tezos node rpc request. Corr-Id $cid: ${request.uri}")
      }
      for {
        response <- Http(system).singleRequest(request)
        strict <- response.entity.toStrict(requestConfig.POSTResponseEntityTimeout)
      } yield {
        val responseBody = strict.data.utf8String
        if (node.traceCalls) {
          asyncPostLogger.debug(s"Tezos node response. Corr-Id $cid: $response")
          asyncPostLogger.debug(s"Tezos node rpc json payload. Corr-Id $cid: $responseBody")
        }
        JsonString sanitize responseBody
      }
    }

  /* Connection pool settings customized for streaming requests */
  protected[tezos] val streamingRequestsConnectionPooling: ConnectionPoolSettings = ConnectionPoolSettings(
    streamingConfig.pool
  )

  /* creates a connections pool based on the host network */
  private def getHostPoolFlow[T] =
    if (node.protocol == "https")
      Http(system).cachedHostConnectionPoolHttps[T](
        host = node.hostname,
        port = node.port,
        settings = streamingRequestsConnectionPooling
      )
    else
      Http(system).cachedHostConnectionPool[T](
        host = node.hostname,
        port = node.port,
        settings = streamingRequestsConnectionPooling
      )

  /* Wraps the request/response flow with logging, if enabled by configuration
   * The returned flow is the streaming req/res exchange, paired with a correlation T
   */
  private def loggedRpcFlow[T] =
    if (node.traceCalls)
      Flow[(HttpRequest, T)]
        .log("Tezos node rpc request", { case (req, cid) => s"Corr-Id $cid: ${req.uri}" })(log = batchedRpcTraceLogger)
        .withAttributes(
          Attributes.logLevels(onElement = LogLevels.Info, onFinish = LogLevels.Info, onFailure = LogLevels.Error)
        )
        .via(getHostPoolFlow[T])
        .log("Tezos node response", { case (res, cid) => s"Corr-id $cid: ${res}" })(log = batchedRpcTraceLogger)
        .withAttributes(
          Attributes.logLevels(onElement = LogLevels.Info, onFinish = LogLevels.Info, onFailure = LogLevels.Error)
        )
    else getHostPoolFlow[T]

  private def loggedRpcResults[T] =
    if (node.traceCalls)
      Flow[(T, String)]
        .log("Tezos node rpc json payload", { case (cid, json) => s"Corr-id $cid: $json" })(log = batchedRpcTraceLogger)
        .withAttributes(
          Attributes.logLevels(onElement = LogLevels.Info, onFinish = LogLevels.Info, onFailure = LogLevels.Error)
        )
    else
      Flow[(T, String)].map(identity)

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
  private def streamedGetQuery[CID](
      ids: List[CID],
      mapToCommand: CID => String,
      concurrencyLevel: Int
  ): Source[(CID, String), akka.NotUsed] = withRejectionControl {

    val convertIdToUrl = mapToCommand andThen translateCommandToUrl

    //we need to thread the id all through the streaming http stages
    val uris = Source(ids.map(id => (convertIdToUrl(id), id)))

    val toRequest: ((String, CID)) => (HttpRequest, CID) = {
      case (url, id) =>
        logger.debug(s"Will query: $url")
        (HttpRequest(uri = Uri(url)), id)
    }

    uris
      .map(toRequest)
      .via(loggedRpcFlow)
      .mapAsyncUnordered(concurrencyLevel) {
        case (tried, id) =>
          Future
            .fromTry(tried.map(_.entity.toStrict(requestConfig.GETResponseEntityTimeout)))
            .flatten
            .map(entity => (entity, id))
      }
      .map { case (content: HttpEntity.Strict, id) => (id, JsonString sanitize content.data.utf8String) }
      .via(loggedRpcResults)

  }

  override def runBatchedGetQuery[CID](
      network: String,
      ids: List[CID],
      mapToCommand: CID => String,
      concurrencyLevel: Int
  ): Future[List[(CID, String)]] = {
    val batchId = java.util.UUID.randomUUID()
    logger.debug(s"$batchId - New batched GET call for ${ids.size} requests")

    streamedGetQuery(ids, mapToCommand, concurrencyLevel)
      .runFold(List.empty[(CID, String)])(_ :+ _)
      .andThen {
        case _ => logger.debug(s"$batchId - Batch completed")
      }
  }

}
