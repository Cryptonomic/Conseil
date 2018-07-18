package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.Lorre.{db}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, SECONDS}
import scala.util.{Failure, Success, Try}

/**
  * Helper classes and functions used for average fee calculations.
  */
object FeeOperations extends LazyLogging {

  private val conf = ConfigFactory.load
  private val awaitTimeInSeconds = conf.getInt("dbAwaitTimeInSeconds")

  /**
    * Representation of estimation of average fees for a given operation kind.
    * @param low       Medium - one standard deviation
    * @param medium    The mean of fees on a given operation kind
    * @param high      Medium + one standard deviation
    * @param timestamp The timestamp when the calculation took place
    * @param kind      The kind of operation being averaged over
    */
  case class AverageFees(
                 low: Int,
                 medium: Int,
                 high: Int,
                 timestamp: java.sql.Timestamp,
                 kind: String
                 )

  /**
    * Calculates average fees for each operation kind and stores them into a fees table.
    * @return
    */
  def processTezosAverageFees(): Try[Unit] = {
    logger.info("Processing latest Tezos fee data...")
    val operationKinds = List("seed_nonce_revelation", "delegation", "transaction", "activate_account", "origination", "reveal", "double_endorsement_evidence", "endorsement")
    val fees = operationKinds.map{ kind =>
      TezosDatabaseOperations.calculateAverageFees(kind)
    }
    Try {
      val dbFut = TezosDatabaseOperations.writeFeesToDatabase(fees, db)
      dbFut onComplete {
        case Success(_) => logger.info(s"Wrote average fees to the database.")
        case Failure(e) => logger.error(s"Could not write average fees to the database because $e")
      }
      Await.result(dbFut, Duration.Inf)
    }
  }

}
