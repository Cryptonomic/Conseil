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

  //processTezosBlocks()
  processTezosAccounts()
  db.close()

  def processTezosBlocks() = {
    TezosNodeOperations.getBlocks("alphanet", 24999, 25000, Some("BLekzFkZ2cZsnGpfZgyVRyQA6r3i3TJXykpYMthuBtiYY6XnBjd")) match {
      case Success(blocks) => {
        Try {
          val dbFut = TezosDatabaseOperations.writeBlocksToDatabase(blocks, db)
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

  def processTezosAccounts() = {
    TezosNodeOperations.getAccounts("alphanet", "BLekzFkZ2cZsnGpfZgyVRyQA6r3i3TJXykpYMthuBtiYY6XnBjd") match {
      case Success(accountsInfo) =>
        Try {
          val dbFut = TezosDatabaseOperations.writeAccountsToDatabase(accountsInfo, db)
          dbFut onComplete {
            _ match {
              case Success(_) => logger.info(s"Wrote ${accountsInfo.accounts.size} accounts to the database.")
              case Failure(e) => logger.error(s"Could not write accounts to the database because ${e}")
            }
          }
          Await.result(dbFut, Duration.Inf)
        }
      case Failure(e) => logger.error(s"Could not fetch accounts from client because ${e}")
    }
  }


}
