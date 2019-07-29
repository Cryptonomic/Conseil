package tech.cryptonomic.conseil.config

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import cats.effect.IO
import cats.effect.concurrent.Ref
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import pureconfig.generic.auto._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Security extends ErrorAccumulatingCirceSupport with LazyLogging {

  /** Keys cache */
  private val nautilusCloudKeys: Ref[IO, Set[String]] = Ref.of[IO, Set[String]](Set.empty).unsafeRunSync()

  /** Updates API keys from Nautilus-Cloud endpoint */
  def updateKeys(
      ncc: NautilusCloudConfiguration
  )(implicit executionContext: ExecutionContext, system: ActorSystem, mat: Materializer): Unit = {
    val update = for {
      apiKeys <- Http()
        .singleRequest(
          HttpRequest(uri = makeUri(ncc))
            .withHeaders(RawHeader("X-Api-Key", ncc.key))
        )
        .flatMap(Unmarshal(_).to[Set[String]])
      _ <- nautilusCloudKeys.set(apiKeys).unsafeToFuture()
    } yield apiKeys

    update onComplete {
      case Success(apiKeys) => logger.info("Managed to update api keys with {}", apiKeys)
      case Failure(exception) => logger.error("Error during API keys update", exception)
    }
  }

  private def makeUri(ncc: NautilusCloudConfiguration): String =
    s"${ncc.host}:${ncc.port}/${ncc.path}"

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
      nautilusCloudKeys.get.map(ncKeys => (ncKeys ++ keys).contains(candidateApiKey)).unsafeToFuture()
  }

}
