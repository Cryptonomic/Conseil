package tech.cryptonomic.conseil.api.security

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
import pureconfig.error.{ConfigReaderFailures, ThrowableFailure}
import pureconfig.generic.auto._
import tech.cryptonomic.conseil.api.config.NautilusCloudConfiguration

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
    } yield ()

    update onComplete {
      case Success(_) => logger.info("Managed to update API keys")
      case Failure(exception) => logger.error("Error during API keys update", exception)
    }
  }

  private def makeUri(ncc: NautilusCloudConfiguration): String =
    s"${ncc.host}:${ncc.port}/${ncc.path}"

  final case class InvalidSecurityConfiguration(message: String) extends RuntimeException(message)

  /** creates security data from configuration */
  def apply(): Either[pureconfig.error.ConfigReaderFailures, SecurityApi] =
    pureconfig
      .loadConfig[SecurityApi](namespace = "conseil.security.apiKeys")
      .filterOrElse(
        _.isValid,
        ConfigReaderFailures(
          ThrowableFailure(
            InvalidSecurityConfiguration(
              "Security configuration is invalid. When allowBlank is set to false you need to provide at least one api key"
            ),
            None
          )
        )
      )

  final case class SecurityApi(keys: Set[String], allowBlank: Option[Boolean]) extends Product with Serializable {

    /**
      * Determines whether a configuration is valid
      *
      * @return True is valid, false otherwise.
      */
    def isValid: Boolean = allowBlank.contains(true) || keys.nonEmpty

    /**
      * Determines whether a given API key is valid.
      *
      * @param candidateApiKey The given API key
      * @return True is valid, false otherwise.
      */
    def validateApiKey(candidateApiKey: Option[String]): Future[Boolean] =
      nautilusCloudKeys.get
        .map(_ ++ keys)
        .map(allApiKeys => candidateApiKey.fold(allowBlank.contains(true))(allApiKeys.contains))
        .unsafeToFuture()
  }
}
