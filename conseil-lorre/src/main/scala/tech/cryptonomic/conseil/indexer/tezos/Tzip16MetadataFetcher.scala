package tech.cryptonomic.conseil.indexer.tezos

import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.Attributes.LogLevels
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.{ActorMaterializer, Attributes}
import cats.data.Kleisli
import scribe.Level
import tech.cryptonomic.conseil.common.config.Platforms.TezosConfiguration
import tech.cryptonomic.conseil.common.generic.chain.DataFetcher
import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.fetch
import tech.cryptonomic.conseil.common.io.Logging.{ConseilLogSupport, ConseilLogger}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{InternalOperationResults, _}
import tech.cryptonomic.conseil.common.util.JsonUtil.JsonString
import tech.cryptonomic.conseil.indexer.LorreIndexer.ShutdownComplete
import tech.cryptonomic.conseil.indexer.config.{BatchFetchConfiguration, HttpStreamingConfiguration, NetworkCallsConfiguration}

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.control.NoStackTrace
import scala.util.{Failure, Try}


case class Tzip16Metadata(
  name: String,
  description: String,
  version: Option[String],
  license: Option[Tzip16License],
  authors: Option[List[String]],
  homepage: Option[String],
  source: Option[Tzip16Source],
  interfaces: Option[List[String]]
)

case class Tzip16Source(tools: Option[List[String]], location: Option[String])

case class Tzip16License(name: Option[String], details: Option[String])

object Tzip16MetadataJsonDecoders {

  import io.circe._
  import io.circe.generic.semiauto._

  implicit val tzip16MetadataJsonDecoder: Decoder[Tzip16Metadata] = deriveDecoder
  implicit val tzip16SourceJsonDecoder: Decoder[Tzip16Source] = deriveDecoder
  implicit val tzip16LicenseJsonDecoder: Decoder[Tzip16License] = deriveDecoder

}

class Tzip16MetadataOperator(
  val node: TezosMetadataInterface,
  batchConf: BatchFetchConfiguration
)(implicit val fetchFutureContext: ExecutionContext) extends ConseilLogSupport {

  import TezosJsonDecoders.Circe.decodeLiftingTo
  import Tzip16MetadataJsonDecoders._
  import cats.instances.future._
  import cats.syntax.applicative._
  import cats.syntax.applicativeError._

  private type FutureFetcher = DataFetcher[Future, List, Throwable]

  def getMetadata(addresses: List[(InternalOperationResults.Transaction, String)]):
  Future[List[((InternalOperationResults.Transaction, String), (String, Tzip16Metadata))]] =
    fetch[(InternalOperationResults.Transaction, String), (String, Tzip16Metadata), Future, List, Throwable].run(addresses)

  implicit val metadataFetcher: FutureFetcher {
    type In = (InternalOperationResults.Transaction, String)

    type Out = (String, Tzip16Metadata)

    type Encoded = String
  } = new FutureFetcher {

    /** the input type, e.g. ids of data */
    override type In = (InternalOperationResults.Transaction, String)

    /** the output type, e.g. the decoded block data */
    override type Out = (String, Tzip16Metadata)

    /** the encoded representation type used e.g. some Json representation */
    override type Encoded = String

    private val makeUrl = (key: In) => key._2

    /** an effectful function from a collection of inputs `T[In]`
     * to the collection of encoded values, tupled with the corresponding input `T[(In, Encoded)]`
     */
    override val fetchData =
      Kleisli(
        fetchKeys => {
          val hashes = fetchKeys
          logger.info("Fetching tzip metadata")
          node.runBatchedGetQuery(fetchKeys, makeUrl, 2).onError {
            case err =>
              val showHashes = hashes.mkString(", ")
              logger
                .error(
                  s"I encountered problems while fetching baking rights from TZIP-16 for blocks $showHashes. The error says ${err.getMessage}"
                )
                .pure[Future]
          }
        }
      )

    /** an effectful function that decodes the json value to an output `Out`*/
    override val decodeData: Kleisli[Future, String, (String, Tzip16Metadata)] = Kleisli { json =>
      decodeLiftingTo[Future, Tzip16Metadata](json).map(json -> _)
        .onError(
          logWarnOnJsonDecoding(
            s"I fetched TZIP-16 json from tezos node that I'm unable to decode: $json",
            ignore = Option(json).forall(_.trim.isEmpty)
          )
        )

    }
  }

  private def logWarnOnJsonDecoding[Encoded](
    message: String,
    ignore: Boolean = false
  ): PartialFunction[Throwable, Future[Unit]] = {
    case decodingError: io.circe.Error if ignore =>
      ().pure[Future]
    case decodingError: io.circe.Error =>
      decodingError.fillInStackTrace()
      logger.warn(message, decodingError).pure[Future]
    case t =>
      logger.error("Something unexpected failed while decoding json", t).pure[Future]
  }
}

class TezosMetadataInterface(
  config: TezosConfiguration,
  requestConfig: NetworkCallsConfiguration,
  streamingConfig: HttpStreamingConfiguration
)(implicit system: ActorSystem)
  extends ConseilLogSupport {

  import config.node

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  final val traceLoggingGetCategory = "tech.cryptonomic.conseil.indexer.tezos.node-rpc.get"
  final val traceLoggingBatchCategory = "tech.cryptonomic.conseil.indexer.tezos.node-rpc.batch"
  private[this] lazy val rejected = Failure(
    new IllegalStateException("Tezos node requests will no longer be accepted.") with NoStackTrace
  )
  private lazy val asyncGetLogger =
    ConseilLogger(traceLoggingGetCategory, Some(Level.Debug))
  /* Defines the custom akka logging category to emit request-response traces on each stream element */
  private lazy val batchedRpcTraceLogger: LoggingAdapter =
    Logging(system.eventStream, traceLoggingBatchCategory)
  /* Connection pool settings customized for streaming requests */
  protected[tezos] val streamingRequestsConnectionPooling: ConnectionPoolSettings = ConnectionPoolSettings(
    streamingConfig.pool
  )
  private[this] val rejectingCalls = new java.util.concurrent.atomic.AtomicBoolean(false)

  def shutdown(): Future[ShutdownComplete] = {
    rejectingCalls.compareAndSet(false, true)
    Http(system).shutdownAllConnectionPools().map(_ => ShutdownComplete)
  }

  def withRejectionControl[T](call: => Try[T]): Try[T] =
    if (rejectingCalls.get) rejected else call

  def runAsyncGetQuery(command: String): Future[String] = withRejectionControl {
    val url = translateCommandToUrl(command)
    val request = HttpRequest(HttpMethods.GET, url)

    val cid = UUID.randomUUID().toString
    if (node.traceCalls) {
      asyncGetLogger.debug(s"Tzip node rpc request. Corr-Id $cid: ${request.uri}")
    }

    for {
      response <- Http(system).singleRequest(request)
      strict <- response.entity.toStrict(requestConfig.GETResponseEntityTimeout)
    } yield {
      val responseBody = strict.data.utf8String
      if (node.traceCalls) {
        asyncGetLogger.debug(s"Tzip response. Corr-Id $cid: $response")
        asyncGetLogger.debug(s"Tzip node rpc json payload. Corr-Id $cid: $responseBody")
      }
      JsonString sanitize responseBody
    }

  }

  def withRejectionControl[T](call: => Future[T]): Future[T] =
    if (rejectingCalls.get) Future.fromTry(rejected) else call

  def runBatchedGetQuery[CID](
    ids: List[CID],
    mapToCommand: CID => String,
    concurrencyLevel: Int
  ): Future[List[(CID, String)]] = {
    val batchId = java.util.UUID.randomUUID()
    logger.debug(s"$batchId - New batched Tzip GET call for ${ids.size} requests")

    println(s"XXXXX ${ids}")
    streamedGetQuery(ids, mapToCommand, concurrencyLevel)
      .runFold(List.empty[(CID, String)])(_ :+ _)
      .andThen {
        case xxx =>
//          xxx.toOption.toList.flatten.foreach { zzz =>
////            println()
////            println(zzz._2)
//          }
//          println()
          logger.debug(s"$batchId - Tzip Batch completed")
      }
  }

  /**
   * Creates a stream that will produce http response content for the
   * list of commands.
   *
   * @param ids              correlation ids for each request to send
   * @param mapToCommand     extract a tezos command (uri fragment) from the id
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
    val uris = Source(ids.map{id =>
//      println(convertIdToUrl(id))
      (convertIdToUrl(id), id)
    })

    val toRequest: ((String, CID)) => (HttpRequest, CID) = {
      case (url, id) =>
        logger.debug(s"Tzip Will query: $url")
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

  def withRejectionControl[T](call: => Source[T, NotUsed]): Source[T, NotUsed] =
    if (rejectingCalls.get) Source.empty[T] else call

  private[this] def translateCommandToUrl(command: String): String =
    command match {
      case url if url.startsWith("http") => url
      case url if url.startsWith("ipfs") =>
        s"https://ipfs.infura.io/ipfs/${url.stripPrefix("ipfs://")}"
        //s"https://cloudflare-ipfs.com/ipfs/${url.stripPrefix("ipfs://")}"
    }

  /* Wraps the request/response flow with logging, if enabled by configuration
   * The returned flow is the streaming req/res exchange, paired with a correlation T
   */
  private def loggedRpcFlow[T] =
    if (false)
      Flow[(HttpRequest, T)]
        .log("Tzip node rpc request", { case (req, cid) => s"Corr-Id $cid: ${req.uri}" })(log = batchedRpcTraceLogger)
        .withAttributes(
          Attributes.logLevels(onElement = LogLevels.Info, onFinish = LogLevels.Info, onFailure = LogLevels.Error)
        )
        .via(getHostPoolFlow[T])
        .log("Tzip node response", { case (res, cid) => s"Corr-id $cid: ${res}" })(log = batchedRpcTraceLogger)
        .withAttributes(
          Attributes.logLevels(onElement = LogLevels.Info, onFinish = LogLevels.Info, onFailure = LogLevels.Error)
        )
    else getHostPoolFlow[T]

  /* creates a connections pool based on the host network */
  private def getHostPoolFlow[T] =
      Http(system).cachedHostConnectionPoolHttps[T](
        host = "ipfs.infura.io",
        //host = "cloudflare-ipfs.com",
        port = 443,
        settings = streamingRequestsConnectionPooling
      )


  private def loggedRpcResults[T] =
    if (node.traceCalls)
      Flow[(T, String)]
        .log("Tzip node rpc json payload", { case (cid, json) => s"Corr-id $cid: $json" })(log = batchedRpcTraceLogger)
        .withAttributes(
          Attributes.logLevels(onElement = LogLevels.Info, onFinish = LogLevels.Info, onFailure = LogLevels.Error)
        )
    else
      Flow[(T, String)].map(identity)

}

