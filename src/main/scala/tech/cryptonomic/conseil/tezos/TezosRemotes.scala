package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.config.{HttpStreamingConfiguration, NetworkCallsConfiguration}
import tech.cryptonomic.conseil.config.Platforms.TezosConfiguration
import tech.cryptonomic.conseil.generic.chain.RemoteRpc
import tech.cryptonomic.conseil.util.JsonUtil.JsonString


object TezosRemoteInstances {

  object Akka {
    import com.typesafe.scalalogging.Logger
    import cats.Monoid
    import scala.concurrent.Future
    import akka.actor.ActorSystem
    import akka.stream.ActorMaterializer
    import akka.stream.scaladsl.Source
    import akka.http.scaladsl.Http
    import akka.http.scaladsl.model._

    case class RemoteContext(
      tezosConfig: TezosConfiguration,
      requestConfig: NetworkCallsConfiguration,
      streamingConfig: HttpStreamingConfiguration
    )(implicit val system: ActorSystem) {

      object ShutdownComplete extends ShutdownComplete
      private val rejectingCalls = new java.util.concurrent.atomic.AtomicBoolean(false)

      def translateCommandToUrl(command: String): String = {
        import tezosConfig.nodeConfig._
        s"$protocol://$hostname:$port/${pathPrefix}chains/main/$command"
      }

      def shutdown(): Future[ShutdownComplete] = {
        rejectingCalls.compareAndSet(false, true)
        Http(system).shutdownAllConnectionPools().map(_ => this.ShutdownComplete)(system.dispatcher)
      }

      def withRejectionControl[T, Container[_]](call: => Container[T])(implicit mono: Monoid[Container[T]]): Container[T] =
        if (rejectingCalls.get) mono.empty else call

    }

    object Futures extends Futures

    trait Futures {
      import cats.Id
      import cats.data.Const
      import cats.instances.future._
      import cats.syntax.apply._
      import scala.concurrent.ExecutionContext
      import scala.util.control.NoStackTrace

      //might be actually unlawful, but we won't use it for append, only for empty
      private implicit def futureMonoid[T](implicit ec: ExecutionContext) = new Monoid[Future[T]] {
        override def combine(x: Future[T], y: Future[T]) = x *> y
        override def empty: Future[T] = Future.failed(new IllegalStateException("Tezos node requests will no longer be accepted.") with NoStackTrace)
      }

      type JustString[CallId] = Const[String, CallId]

      implicit def futuresInstance(implicit context: RemoteContext) =
        new RemoteRpc[Future, Id, JustString] {
          import context._

          private val logger = Logger("Akka.Futures.RemoteRpc")

          implicit val system = context.system
          implicit val dispatcher = system.dispatcher
          implicit val materializer = ActorMaterializer()

          type CallConfig = Any
          type PostPayload = JsonString

          def runGetCall[CallId](
            callConfig: Any = (),
            request: CallId,
            commandMap: CallId => String
          ): Future[JustString[CallId]] = withRejectionControl {

            val url = (commandMap andThen translateCommandToUrl)(request)
            val httpRequest = HttpRequest(HttpMethods.GET, url)
            logger.debug("Async querying URL {} for platform Tezos and network {}", url, tezosConfig.network)

            for {
              response <- Http(system).singleRequest(httpRequest)
              strict <- response.entity.toStrict(requestConfig.GETResponseEntityTimeout)
            } yield Const(JsonString sanitize strict.data.utf8String)

          }

          def runPostCall[CallId](
            callConfig: Any = (),
            request: CallId,
            commandMap: CallId => String,
            payload: Option[JsonString]
          ): Future[JustString[CallId]] = withRejectionControl {

            val url = (commandMap andThen translateCommandToUrl)(request)
            logger.debug("Async querying URL {} for platform Tezos and network {} with payload {}", url, tezosConfig.network, payload)
            val postedData = payload.getOrElse(JsonString.emptyObject)
            val httpRequest = HttpRequest(
              HttpMethods.POST,
              url,
              entity = HttpEntity(ContentTypes.`application/json`, postedData.json.getBytes())
            )

            for {
              response <- Http(system).singleRequest(httpRequest)
              strict <- response.entity.toStrict(requestConfig.POSTResponseEntityTimeout)
            } yield {
              val responseBody = strict.data.utf8String
              logger.debug("Query results: {}", responseBody)
              Const(JsonString sanitize responseBody)
            }
          }
        }
    }

    object Streams extends Streams {

      type ConcurrencyLevel = Int
      type StreamSource[A] = Source[A, akka.NotUsed]
      type TaggedString[CallId] = (CallId, String)

    }

    trait Streams {
      import Streams._
      import akka.http.scaladsl.settings.ConnectionPoolSettings

      //might be actually unlawful, but we won't use it for append, only for empty
      private implicit def sourceMonoid[T] = new Monoid[StreamSource[T]] {
        override def combine(x: StreamSource[T], y: StreamSource[T]) = x ++ y
        override def empty: StreamSource[T] = Source.empty[T]
      }

      /** override this to handle failures on the Get calls
        * @param url the failing target
        * @param err the actual failure
        */
      def handleErrorsOnGet(url: String, err: Throwable): Unit = ()

      /** An instance available to stream GET requests, returning a stream of utf-8 results, paired with the input
        * @param context we need to convert to the fetcher, based on an implicit `RemoteContext`
        */
      implicit def streamsInstance(implicit context: RemoteContext) =
        new RemoteRpc[StreamSource, StreamSource, TaggedString] {
          import context._
          import context.tezosConfig.nodeConfig

          implicit val system = context.system
          implicit val dispatcher = system.dispatcher
          implicit val materializer = ActorMaterializer()

          //no real support for POST calls
          type PostPayload = Nothing
          //will pass the concurrency level used in the internal stream as additional call information
          type CallConfig = ConcurrencyLevel

          override def runGetCall[CallId](
            callConfig: ConcurrencyLevel,
            request: StreamSource[CallId],
            commandMap: CallId => String
          ): StreamSource[(CallId, String)] = withRejectionControl {
            val convertIdToUrl = commandMap andThen translateCommandToUrl
            val toRequest: ((String, CallId)) => (HttpRequest, CallId) = { case (url, id) => HttpRequest(uri = Uri(url)) -> id }

            request.map(id => convertIdToUrl(id) -> id)
              .map(toRequest)
              .via(getHostPoolFlow)
              .mapAsyncUnordered(callConfig) {
                case (tried, id) =>
                  tried.failed.foreach(handleErrorsOnGet(convertIdToUrl(id), _))
                  Future.fromTry(tried)
                    .flatMap(_.entity.toStrict(requestConfig.GETResponseEntityTimeout))
                    .map(content => id -> JsonString.sanitize(content.data.utf8String))
              }
          }

          /* this is not expected to ever get called
           * as testified by the empty payload
           */
          override def runPostCall[CallId](
            callConfig: ConcurrencyLevel,
            request: StreamSource[CallId],
            commandMap: CallId => String,
            payload: Option[Nothing]
          ): StreamSource[(CallId, String)] = withRejectionControl {
            Source.empty
          }

          /* Connection pool settings customized for streaming requests */
          private val streamingRequestsConnectionPooling: ConnectionPoolSettings = ConnectionPoolSettings(streamingConfig.pool)

          /* creates a connections pool based on the host network */
          private[this] def getHostPoolFlow[T] = {
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
          }

        }

    }
  }
}