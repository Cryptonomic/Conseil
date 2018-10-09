package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import slick.dbio.DBIOAction
import tech.cryptonomic.conseil.Lorre.db
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Helper classes and functions used for average fee calculations.
  */
object FeeOperations extends LazyLogging {

  private val operationKinds = List(
    "seed_nonce_revelation",
    "delegation",
    "transaction",
    "activate_account",
    "origination",
    "reveal",
    "double_endorsement_evidence",
    "endorsement"
  )

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
  def processTezosAverageFees()(implicit ex: ExecutionContext): Future[Option[Int]] = {
    logger.info("Processing latest Tezos fee data...")
    val computeAndStore = for {
      fees <- DBIOAction.sequence(operationKinds.map(TezosDb.calculateAverageFeesIO))
      dbWrites <- TezosDb.writeFeesIO(fees.collect { case Some(fee) => fee })
    } yield dbWrites

    db.run(computeAndStore)
      .andThen{
        case Success(Some(written)) => logger.info("Wrote {} average fees to the database.", written)
        case Success(None) => logger.info("Wrote average fees to the database.")
        case Failure(e) => logger.error("Could not write average fees to the database because", e)
      }

  }

}
