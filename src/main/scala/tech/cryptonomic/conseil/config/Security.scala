package tech.cryptonomic.conseil.config

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import cats.effect.IO
import cats.effect.concurrent.Ref
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import pureconfig.generic.auto._

import scala.concurrent.{ExecutionContext, Future}


object Security extends ErrorAccumulatingCirceSupport {

  /** Keys cache */
  private val nautilusCloudKeys: Ref[IO, Set[String]] = Ref.of[IO, Set[String]](Set.empty).unsafeRunSync()

  def updateKeys(ncc: NautilusCloudConfiguration)
    (implicit executionContext: ExecutionContext, system: ActorSystem, mat: Materializer): Unit = {
    for {
      apiKeys <- Http()
        .singleRequest(
          HttpRequest(uri = makeUri(ncc))
            .withHeaders(RawHeader("X-Api-Key", ncc.key))
        ).flatMap(Unmarshal(_).to[Set[String]])
      _ <- nautilusCloudKeys.update (_ => apiKeys).unsafeToFuture()
    } yield ()
  }

  private def makeUri(ncc: NautilusCloudConfiguration): String = {
    s"${ncc.host}:${ncc.port}/${ncc.path}"
  }

  /** creates security data from configuration */
  def apply(): Either[pureconfig.error.ConfigReaderFailures, SecurityApi] =
    pureconfig.loadConfig[SecurityApi](namespace = "security.apiKeys.keys")

  final case class SecurityApi(keys: Set[String]) extends AnyVal with Product with Serializable {

    /**
      * Determines whether a given API key is valid.
      *
      * @param candidateApiKey The given API key
      * @return True is valid, false otherwise.
      */
    def validateApiKey(candidateApiKey: String): Future[Boolean] =
      nautilusCloudKeys.get.map(ncKeys => (ncKeys ++ keys) (candidateApiKey)).unsafeToFuture()
  }

}