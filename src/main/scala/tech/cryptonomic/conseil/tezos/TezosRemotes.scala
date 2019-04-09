package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.config.{HttpStreamingConfiguration, NetworkCallsConfiguration}
import tech.cryptonomic.conseil.config.Platforms.TezosConfiguration
import tech.cryptonomic.conseil.generic.chain.RemoteRpc
import tech.cryptonomic.conseil.util.JsonUtil.JsonString


object TezosRemotesInstances {

  object Akka {
    import com.typesafe.scalalogging.Logger
    import cats.Monoid
    import scala.concurrent.Future
    import akka.actor.ActorSystem
    import akka.stream.ActorMaterializer
    import akka.stream.scaladsl.Source
    import akka.http.scaladsl.Http
    import akka.http.scaladsl.model._

    case class TezosNodeInterface(
      config: TezosConfiguration,
      requestConfig: NetworkCallsConfiguration,
      streamingConfig: HttpStreamingConfiguration
    )(implicit system: ActorSystem) {

      object ShutdownComplete extends ShutdownComplete
      private val rejectingCalls = new java.util.concurrent.atomic.AtomicBoolean(false)

      def translateCommandToUrl(command: String): String = {
        import config.nodeConfig._
        s"$protocol://$hostname:$port/${pathPrefix}chains/main/$command"
      }

      def shutdown() = {
        rejectingCalls.compareAndSet(false, true)
        Http(system).shutdownAllConnectionPools().map(_ => this.ShutdownComplete)(system.dispatcher)
      }

      def withRejectionControl[T, Container[_]](call: => Container[T])(implicit mono: Monoid[Container[T]]): Container[T] =
        if (rejectingCalls.get) mono.empty else call

    }

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

      implicit def remote(tezosInterface: TezosNodeInterface)(implicit system: ActorSystem) =
        new RemoteRpc[Future, Id, JustString] {
          import tezosInterface.{translateCommandToUrl, withRejectionControl}

          private val logger = Logger("Akka.Futures.RemoteRpc")

          implicit val materializer = ActorMaterializer()
          implicit val dispatcher = system.dispatcher

          type CallConfig = Any
          type PostPayload = JsonString

          def runGetCall[CallId](
            callConfig: Any = (),
            request: CallId,
            commandMap: CallId => String
          ): Future[JustString[CallId]] = withRejectionControl {

            val url = (commandMap andThen translateCommandToUrl)(request)
            val httpRequest = HttpRequest(HttpMethods.GET, url)
            logger.debug("Async querying URL {} for platform Tezos and network {}", url, tezosInterface.config.network)

            for {
              response <- Http(system).singleRequest(httpRequest)
              strict <- response.entity.toStrict(tezosInterface.requestConfig.GETResponseEntityTimeout)
            } yield Const(JsonString sanitize strict.data.utf8String)

          }

          def runPostCall[CallId](
            callConfig: Any = (),
            request: CallId,
            commandMap: CallId => String,
            payload: Option[JsonString]
          ): Future[JustString[CallId]] = withRejectionControl {

            val url = (commandMap andThen translateCommandToUrl)(request)
            logger.debug("Async querying URL {} for platform Tezos and network {} with payload {}", url, tezosInterface.config.network, payload)
            val postedData = payload.getOrElse(JsonString.emptyObject)
            val httpRequest = HttpRequest(
              HttpMethods.POST,
              url,
              entity = HttpEntity(ContentTypes.`application/json`, postedData.json.getBytes())
            )

            for {
              response <- Http(system).singleRequest(httpRequest)
              strict <- response.entity.toStrict(tezosInterface.requestConfig.POSTResponseEntityTimeout)
            } yield {
              val responseBody = strict.data.utf8String
              logger.debug("Query results: {}", responseBody)
              Const(JsonString sanitize responseBody)
            }
          }
        }
    }

    trait Streams {
      import akka.http.scaladsl.settings.ConnectionPoolSettings

      //might be actually unlawful, but we won't use it for append, only for empty
      private implicit def sourceMonoid[T] = new Monoid[StreamSource[T]] {
        override def combine(x: StreamSource[T], y: StreamSource[T]) = x ++ y
        override def empty: StreamSource[T] = Source.empty[T]
      }

      type ConcurrencyLevel = Int
      type StreamSource[A] = Source[A, akka.NotUsed]
      type TaggedString[CallId] = (CallId, String)


      implicit def remote(tezosInterface: TezosNodeInterface)(implicit system: ActorSystem) =
        new RemoteRpc[StreamSource, StreamSource, TaggedString] {
          import tezosInterface.{translateCommandToUrl, withRejectionControl}
          import tezosInterface.config.nodeConfig

          private val logger = Logger("Akka.Streams.RemoteRpc")

          implicit val materializer = ActorMaterializer()
          implicit val dispatcher = system.dispatcher

          type PostPayload = Nothing
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
                  Future.fromTry(tried)
                    .flatMap(_.entity.toStrict(tezosInterface.requestConfig.GETResponseEntityTimeout))
                    .map(content => id -> JsonString.sanitize(content.data.utf8String))
              }
          }

          override def runPostCall[CallId](
            callConfig: ConcurrencyLevel,
            request: StreamSource[CallId],
            commandMap: CallId => String,
            payload: Option[Nothing]
          ): StreamSource[(CallId, String)] =
            ???

          /* Connection pool settings customized for streaming requests */
          private val streamingRequestsConnectionPooling: ConnectionPoolSettings = ConnectionPoolSettings(tezosInterface.streamingConfig.pool)

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