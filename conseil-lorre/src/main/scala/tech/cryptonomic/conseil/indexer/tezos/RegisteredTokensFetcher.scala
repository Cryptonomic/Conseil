package tech.cryptonomic.conseil.indexer.tezos

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import cats.effect.IO
import cats.effect.concurrent.Ref
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import pureconfig.error.{ConfigReaderFailures, ThrowableFailure}
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.indexer.config.TokenContracts
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.util.DatabaseUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object RegisteredTokensFetcher extends ErrorAccumulatingCirceSupport with ConseilLogSupport {
  import kantan.csv.generic._


  /** Updates API keys from Nautilus-Cloud endpoint */
  def updateKeys(
    tc: TokenContracts
  )(implicit executionContext: ExecutionContext, system: ActorSystem, mat: Materializer): Unit = {
    val update = for {
      apiKeys <- Http()
        .singleRequest(
          HttpRequest(uri = tc.url)
        )
        .flatMap(_.entity.toStrict(10.seconds).map(_.data.utf8String))
      _ = logger.info(s"Got... $apiKeys")
      _ <- TezosDatabaseOperations.initTableFromCsvString(DatabaseUtil.lorreDb, Tables.RegisteredTokens, apiKeys, upsert = true)

    } yield ()

    update onComplete {
      case Success(_) => logger.info("Managed to update API keys")
      case Failure(exception) => logger.error("Error during API keys update", exception)
    }
  }




}
