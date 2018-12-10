package tech.cryptonomic.conseil.tezos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.util.JsonUtil.JsonString

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
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
    * @param network  Which Tezos network to go against
    * @param ids  correlation ids for each request to send
    * @param mapToCommand  extract a tezos command (uri fragment) from the id
    * @param concurrencyLevel the concurrency in processing the responses
    * @return pairing of the correlation id with the string http response content
    */
  def runBatchedGetQuery[ID](
    network: String,
    ids: List[ID],
    mapToCommand: ID => String,
    concurrencyLevel: Int): Future[List[(ID, String)]]

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
  * Shared configurations related to node operation
  */
object TezosNodeConfig {
  private[tezos] val conf = ConfigFactory.load
  private[tezos] val awaitTime = conf.getInt("dbAwaitTimeInSeconds").seconds
  private[tezos] val entityGetTimeout = conf.getInt("GET-ResponseEntityTimeoutInSeconds").seconds
  private[tezos] val entityPostTimeout = conf.getInt("POST-ResponseEntityTimeoutInSeconds").seconds
}

/**
  * Concrete implementation of the above.
  */
class TezosNodeInterface(implicit system: ActorSystem) extends TezosRPCInterface with LazyLogging {
  import TezosNodeConfig._

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

  private[this] def translateCommandToUrl(network: String, command: String): String = {
    val protocol = conf.getString(s"platforms.tezos.$network.node.protocol")
    val hostname = conf.getString(s"platforms.tezos.$network.node.hostname")
    val port = conf.getInt(s"platforms.tezos.$network.node.port")
    val pathPrefix = conf.getString(s"platforms.tezos.$network.node.pathPrefix")
    s"$protocol://$hostname:$port/${pathPrefix}chains/main/$command"
  }

  override def runGetQuery(network: String, command: String): Try[String] = withRejectionControl {
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

  override def runAsyncGetQuery(network: String, command: String): Future[String] = withRejectionControl {
    val url = translateCommandToUrl(network, command)
    val request = HttpRequest(HttpMethods.GET, url)
    logger.debug("Async querying URL {} for platform Tezos and network {}", url, network)

    for {
      response <- Http(system).singleRequest(request)
      strict <- response.entity.toStrict(entityGetTimeout)
    } yield strict.data.utf8String

  }

  override def runPostQuery(network: String, command: String, payload: Option[JsonString]= None): Try[String] = withRejectionControl {
    Try {
      val url = translateCommandToUrl(network, command)
      logger.debug(s"Querying URL $url for platform Tezos and network $network with payload $payload")
      val postedData = payload.getOrElse(JsonString.emptyObject)
      val responseFuture: Future[HttpResponse] =
        Http(system).singleRequest(
          HttpRequest(
            HttpMethods.POST,
            url,
            entity = HttpEntity(ContentTypes.`application/json`, postedData.json.getBytes())
          )
        )
      val response: HttpResponse = Await.result(responseFuture, awaitTime)
      val responseBodyFuture = response.entity.toStrict(entityPostTimeout).map(_.data).map(_.utf8String)
      val responseBody = Await.result(responseBodyFuture, awaitTime)
      logger.debug(s"Query result: $responseBody")
      responseBody

    }
  }

  override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonString]= None): Future[String] = withRejectionControl {
    val url = translateCommandToUrl(network, command)
    logger.debug(s"Async querying URL $url for platform Tezos and network $network with payload $payload")
    val postedData = payload.getOrElse(JsonString.emptyObject)
    val request = HttpRequest(
      HttpMethods.POST,
      url,
      entity = HttpEntity(ContentTypes.`application/json`, postedData.json.getBytes())
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
  protected[tezos] val streamingRequestsConnectionPooling: ConnectionPoolSettings = ConnectionPoolSettings(
    conf.getConfig("akka.tezos-streaming-client")
      .atPath("akka.http.host-connection-pool")
      .withFallback(conf)
  )

  /** creates a connections pool based on the host network */
  private[this] def createHostPoolFlow[ID](settings: ConnectionPoolSettings, network: String) = {
    val host = conf.getString(s"platforms.tezos.$network.node.hostname")
    val port = conf.getInt(s"platforms.tezos.$network.node.port")
    val protocol = conf.getString(s"platforms.tezos.$network.node.protocol")
    if (protocol == "https")
      Http(system).newHostConnectionPoolHttps[ID](
        host = host,
        port = port,
        settings = settings
      )
    else
      Http(system).newHostConnectionPool[ID](
        host = host,
        port = port,
        settings = settings
    )
  }
/*
  override def runBatchedGetQuery(
    network: String,
    commands: List[String],
    concurrencyLevel: Int): Future[List[String]] =
    withRejectionControl {
      val connections = createHostPoolFlow(streamingRequestsConnectionPooling, network)
      val uris = Source(commands.map(translateCommandToUrl(network, _)))
      val toRequest = (url: String) => (HttpRequest(uri = Uri(url)), url)

      uris.map(toRequest)
        .via(connections)
        .mapAsync(concurrencyLevel) {
          case (tried, _) =>
            Future.fromTry(tried.map(_.entity.toStrict(entityGetTimeout))).flatten
        }
        .map(_.data.utf8String)
        .toMat(Sink.collection[String, List[String]])(Keep.right)
        .run()
  }
 */
  def runBatchedGetQuery[ID](
    network: String,
    ids: List[ID],
    mapToCommand: ID => String,
    concurrencyLevel: Int): Future[List[(ID, String)]] =
    withRejectionControl {
      val connections = createHostPoolFlow[ID](streamingRequestsConnectionPooling, network)
      val convertIdToUrl = mapToCommand andThen (cmd => translateCommandToUrl(network, cmd))
      //we need to thread the id all through the streaming http stages
      val uris = Source(ids.map( id => (convertIdToUrl(id), id) ))
      val toRequest = (in: (String, ID)) => (HttpRequest(uri = Uri(in._1)), in._2)

      uris.map(toRequest)
        .via(connections)
        .mapAsync(concurrencyLevel) {
          case (tried, id) =>
            Future.fromTry(tried.map(_.entity.toStrict(entityGetTimeout)))
              .flatten
              .map(entity => (entity, id))
        }
        .map{case (content, id) => (id, content.data.utf8String)}
        .toMat(Sink.collection[(ID, String), List[(ID, String)]])(Keep.right)
        .run()
  }
}
