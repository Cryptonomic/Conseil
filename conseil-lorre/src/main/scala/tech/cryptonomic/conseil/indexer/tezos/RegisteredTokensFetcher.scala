package tech.cryptonomic.conseil.indexer.tezos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.semiauto.deriveDecoder
import slick.jdbc.JdbcBackend.Database
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.indexer.LorreIndexer.ShutdownComplete
import tech.cryptonomic.conseil.indexer.config.TokenContracts

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Helpers for fetching and updating registered tokens */
class RegisteredTokensFetcher(db: Database, tc: TokenContracts, terminationSequence: () => Future[ShutdownComplete])
    extends ErrorAccumulatingCirceSupport
    with ConseilLogSupport {

  import RegisteredTokensFetcher.RegisteredToken
  import io.circe.generic.semiauto._

  implicit val decoder = deriveDecoder[RegisteredToken]

  /** Updates Registered tokens table from URL */
  def updateRegisteredTokens(
      implicit executionContext: ExecutionContext,
      system: ActorSystem,
      mat: ActorMaterializer
  ): Unit = {
    val update = for {
      tokens <- Http()
        .singleRequest(
          HttpRequest(uri = tc.url)
        )
        .flatMap(Unmarshal(_).to[List[RegisteredToken]])
      _ = logger.info(s"Got tokens: $tokens")
      _ <- TezosDatabaseOperations.initRegisteredTokensTable(db, tokens)
    } yield ()

    update onComplete {
      case Success(_) => logger.info("Managed to update registered tokens")
      case Failure(exception) =>
        logger.error("Error during registered tokens update", exception)
        terminationSequence()
    }
  }

}

/** Companion object for registered tokens fetcher */
object RegisteredTokensFetcher {
  implicit val decoder = deriveDecoder[RegisteredToken]

  case class RegisteredToken(
      name: String,
      symbol: String,
      decimals: Int,
      interfaces: List[String],
      address: String,
      tokenIndex: Option[Int],
      balanceMap: Int,
      balanceKeyType: String,
      balancePath: String,
      markets: List[String],
      farms: List[String],
      isTzip16: Boolean,
      isNft: Boolean,
      metadataType: Option[String],
      metadataBigMapId: Option[Int],
      metadataBigMapType: Option[String],
      metadataPath: Option[String]
  )
}
