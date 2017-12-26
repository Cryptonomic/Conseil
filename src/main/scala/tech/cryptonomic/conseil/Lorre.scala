package tech.cryptonomic.conseil

import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations, TezosNodeOperations}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object Lorre extends App with LazyLogging {

  lazy val db = Database.forConfig("conseildb")

  processTezos()

  db.close()

  def processTezos() = {
    TezosNodeOperations.getBlocks("alphanet", 0, 1000, Some("BL1warkWwBLTUj5rTFig1Pwvki4KxwxHqtvDNBGurJyBr2gqrc1")) match {
      case Success(blocks) => {
        Try {
          val sortedBlocks = blocks.sortBy(_.metadata.level)
          val dbFut = TezosDatabaseOperations.writeToDatabase(sortedBlocks, db)
          dbFut onComplete {
            _ match {
              case Success(_) => logger.info(s"Wrote ${blocks.size} blocks to the database.")
              case Failure(e) => logger.error(s"Could not write blocks to the database because ${e}")
            }
          }
          Await.result(dbFut, Duration.Inf)
        }
      }
      case Failure(e) => logger.error(s"Could not fetch blocks from client because ${e}")
    }
  }


}
