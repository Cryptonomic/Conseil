package tech.cryptonomic.conseil.indexer.tezos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.util.DatabaseUtil
import tech.cryptonomic.conseil.indexer.config.TokenContracts

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/** Helpers for fetching and updating registered tokens */
object RegisteredTokensFetcher extends ErrorAccumulatingCirceSupport with ConseilLogSupport {

  import io.circe.generic.semiauto._

  implicit val decoder = deriveDecoder[RegisteredToken]

  case class RegisteredToken(
    name: String,
    symbol: String,
    decimals: Int,
    interfaces: List[String],
    address: String,
    tokenIndex: Int,
    balanceMap: Int,
    balanceKeyType: String,
    balancePath: String,
    markets: List[String],
    farms: List[String]
  )

  /** Updates Registered tokens table from URL */
  def updateRegisteredTokens(
    tc: TokenContracts
  )(implicit executionContext: ExecutionContext, system: ActorSystem, mat: Materializer): Unit = {
    val update = for {
      tokens <- Http()
        .singleRequest(
          HttpRequest(uri = tc.url)
        )
        .flatMap(Unmarshal(_).to[List[RegisteredToken]])
      _ = logger.info(s"Got tokens: $tokens")
      _ <- TezosDatabaseOperations.initRegisteredTokensTable(DatabaseUtil.lorreDb, tokens)
    } yield ()

    update onComplete {
      case Success(_) => logger.info("Managed to update registered tokens")
      case Failure(exception) => logger.error("Error during registered tokens update", exception)
    }
  }

}
