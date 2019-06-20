package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import slick.dbio.DBIOAction
import tech.cryptonomic.conseil.Lorre.runOnDb
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}

import scala.concurrent.ExecutionContext
import cats.effect.IO
import cats.syntax.flatMap._
import cats.syntax.apply._

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
  final case class AverageFees(
      low: Int,
      medium: Int,
      high: Int,
      timestamp: java.sql.Timestamp,
      kind: String
  )

  /**
    * Calculates average fees for each operation kind and stores them into a fees table.
    * @param numberOfFeesAveraged a limit on how many of the latest fee values will be used for averaging
    * @param ex the needed ExecutionContext to combine multiple database operations
    * @return a future result of the number of rows stored to db, if supported by the driver
    */
  def processTezosAverageFees(numberOfFeesAveraged: Int)(implicit ex: ExecutionContext): IO[Option[Int]] = {
    logger.info("Processing latest Tezos fee data...")
    val computeAndStore = for {
      fees <- DBIOAction.sequence(operationKinds.map(TezosDb.calculateAverageFees(_, numberOfFeesAveraged)))
      dbWrites <- TezosDb.writeFees(fees.collect { case Some(fee) => fee })
    } yield dbWrites

    runOnDb(computeAndStore)
      .handleErrorWith(
        //logs and rethrows
        err => IO(logger.error("Could not write average fees to the database because", err)) *> IO.raiseError(err)
      )
      .flatTap(
        //logs and keeps the previous value
        written => IO(logger.info("Wrote{} average fees to the database.", written.fold("")(" " + _)))
      )

  }

}
