package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.IOUtils.{lift, IOLogging}
import scala.concurrent.ExecutionContext
import cats.effect.{ContextShift, IO}
import cats.syntax.all._

/**
  * Helper classes and functions used for average fee calculations.
  */
object FeeOperations {

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
      kind: String,
      cycle: Option[Int],
      level: Option[Int]
  )
}

class FeeOperations(db: Database)(implicit cs: ContextShift[IO]) extends LazyLogging with IOLogging {
  import FeeOperations._

  /**
    * Calculates average fees for each operation kind and stores them into a fees table.
    * @param numberOfFeesAveraged a limit on how many of the latest fee values will be used for averaging
    * @param ex the needed ExecutionContext to combine multiple database operations
    * @return a future result of the number of rows stored to db, if supported by the driver
    */
  def processTezosAverageFees(numberOfFeesAveraged: Int)(
      implicit ex: ExecutionContext
  ): IO[Option[Int]] = {
    val computeAndStore = for {
      fees <- DBIOAction.sequence(operationKinds.map(TezosDb.calculateAverageFees(_, numberOfFeesAveraged)))
      dbWrites <- TezosDb.writeFees(fees.collect { case Some(fee) => fee })
    } yield dbWrites

    for {
      _ <- liftLog(_.info("Processing latest Tezos fee data..."))
      written <- lift(db.run(computeAndStore)).onError {
        case e => liftLog(_.error("Could not write average fees to the database because", e))
      }
      _ <- written match {
        case Some(rows) => liftLog(_.info("Wrote {} average fees to the database.", rows))
        case None => liftLog(_.info("Wrote average fees to the database."))
      }
    } yield written

  }

}
