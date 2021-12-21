package tech.cryptonomic.conseil.indexer.tezos

import java.util.UUID

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.Attributes
import akka.stream.Attributes.LogLevels
import akka.stream.scaladsl.{Flow, Source}
import cats.data.Kleisli
import scribe.Level
import tech.cryptonomic.conseil.common.config.Platforms.TezosConfiguration
import tech.cryptonomic.conseil.common.generic.chain.DataFetcher
import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.fetch
import tech.cryptonomic.conseil.common.io.Logging.{ConseilLogSupport, ConseilLogger}
import tech.cryptonomic.conseil.common.util.JsonUtil.JsonString
import tech.cryptonomic.conseil.indexer.LorreIndexer.ShutdownComplete
import tech.cryptonomic.conseil.indexer.config.{HttpStreamingConfiguration, NetworkCallsConfiguration}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.control.NoStackTrace
import scala.util.{Failure, Try}
import cats.implicits._
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.Tables.OperationsRow

/** Helpers for metadata operator */
object Tzip16MetadataJsonDecoders {

  import io.circe._
  import io.circe.generic.semiauto._

  /** Representation of Metadata compient with TZIP-16 format
    * MOre info on the fields can be found here: https://tzip.tezosagora.org/proposal/tzip-16/
    * */
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
  implicit val tzip16MetadataJsonDecoder: Decoder[Tzip16Metadata] = deriveDecoder
  implicit val tzip16SourceJsonDecoder: Decoder[Tzip16Source] = deriveDecoder
  implicit val tzip16LicenseJsonDecoder: Decoder[Tzip16License] = deriveDecoder

}

class Tzip16MetadataOperator(
    val node: TezosMetadataInterface
)(implicit val fetchFutureContext: ExecutionContext)
    extends ConseilLogSupport {

  import Tzip16MetadataJsonDecoders._
  import cats.instances.future._
  import cats.syntax.applicative._
  import cats.syntax.applicativeError._

  private type FutureFetcher = DataFetcher[Future, List, Throwable]

  type MetadataUrl = String

  def getMetadataWithOperationsRow(
      addresses: List[(OperationsRow, MetadataUrl)]
  ): Future[List[((OperationsRow, MetadataUrl), Option[(MetadataUrl, Tzip16Metadata)])]] =
    fetch[(OperationsRow, MetadataUrl), Option[(MetadataUrl, Tzip16Metadata)], Future, List, Throwable]
      .run(addresses)

  def getMetadataWithRegisteredTokensRow(
      addresses: List[(Tables.RegisteredTokensRow, Tables.BigMapContentsRow, MetadataUrl)]
  ): Future[
    List[((Tables.RegisteredTokensRow, Tables.BigMapContentsRow, MetadataUrl), Option[(MetadataUrl, Tzip16Metadata)])]
  ] =
    fetch[(Tables.RegisteredTokensRow, Tables.BigMapContentsRow, MetadataUrl), Option[(MetadataUrl, Tzip16Metadata)], Future, List, Throwable]
      .run(addresses)

  implicit val metadataFetcherRegisteredTokensRow: FutureFetcher {
    type In = (Tables.RegisteredTokensRow, Tables.BigMapContentsRow, MetadataUrl)

    type Out = Option[(MetadataUrl, Tzip16Metadata)]

    type Encoded = String
  } = new FutureFetcher {

    /** the input type, e.g. ids of data */
    override type In = (Tables.RegisteredTokensRow, Tables.BigMapContentsRow, MetadataUrl)

    /** the output type, e.g. the decoded block data */
    override type Out = Option[(MetadataUrl, Tzip16Metadata)]

    /** the encoded representation type used e.g. some Json representation */
    override type Encoded = MetadataUrl

    private val makeUrl = (key: In) => key._3

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
              val showIds = hashes.map(_._2.bigMapId).mkString(", ")
              logger
                .error(
                  s"I encountered problems while fetching metadata from TZIP-16 for big maps $showIds. The error says ${err.getMessage}"
                )
                .pure[Future]
          }
        }
      )

    /** an effectful function that decodes the json value to an output `Out`*/
    override val decodeData: Kleisli[Future, String, Option[(String, Tzip16Metadata)]] = Kleisli { json =>
      import io.circe.parser.decode
      decode[Tzip16Metadata](json).toOption
        .pure[Future]
        .map(_.map(json -> _))
    }
  }

  implicit val metadataFetcherOrigination: FutureFetcher {
    type In = (OperationsRow, MetadataUrl)

    type Out = Option[(MetadataUrl, Tzip16Metadata)]

    type Encoded = String
  } = new FutureFetcher {

    /** the input type, e.g. ids of data */
    override type In = (OperationsRow, MetadataUrl)

    /** the output type, e.g. the decoded block data */
    override type Out = Option[(MetadataUrl, Tzip16Metadata)]

    /** the encoded representation type used e.g. some Json representation */
    override type Encoded = MetadataUrl

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
              val showHashes = hashes.map(_._1.operationId).mkString(", ")
              logger
                .error(
                  s"I encountered problems while fetching metadata from TZIP-16 for operations $showHashes. The error says ${err.getMessage}"
                )
                .pure[Future]
          }
        }
      )

    /** an effectful function that decodes the json value to an output `Out`*/
    override val decodeData: Kleisli[Future, String, Option[(String, Tzip16Metadata)]] = Kleisli { json =>
      import io.circe.parser.decode
      decode[Tzip16Metadata](json).toOption
        .pure[Future]
        .map(_.map(json -> _))
    }
  }
}

class TezosMetadataInterface(
    config: TezosConfiguration,
    requestConfig: NetworkCallsConfiguration,
    streamingConfig: HttpStreamingConfiguration
)(implicit system: ActorSystem)
    extends ConseilLogSupport {

  import config.node

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

    streamedGetQuery(ids, mapToCommand, concurrencyLevel)
      .runFold(List.empty[(CID, String)])(_ :+ _)
      .andThen {
        case _ =>
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
    val uris = Source(ids.map { id =>
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
    }

  /* Wraps the request/response flow with logging, if enabled by configuration
   * The returned flow is the streaming req/res exchange, paired with a correlation T
   */
  private def loggedRpcFlow[T] =
    if (node.traceCalls)
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
