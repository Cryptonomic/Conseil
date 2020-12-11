package tech.cryptonomic.conseil.indexer.tezos

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.indexer.tezos.{TezosDatabaseOperations => TezosDb}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.duration.FiniteDuration

/**
  * Helper classes and functions used for average fee calculations.
  */
private[tezos] object TezosFeeOperations extends ConseilLogSupport {
  // We are getting access to the same database instance, but it should not be a shared object in a long-run.
  // After the splitting we should refactor this part.
  import tech.cryptonomic.conseil.common.util.DatabaseUtil.{lorreDb => db}

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

  /** Calculates average fees for each operation kind and stores them into a fees table.
    * The computation will use a limited number of fees, as the result of a selection window in days.
    * Only blocks belonging within such window in the past, relative to the calculation moment, will be considered.
    *
    * The window is rounded to a granularity of whole days and can't be less than 1.
    *
    * @param selectionWindow the max number of days back from the current block timestamp to use when averaging
    * @param ec the needed ExecutionContext to combine multiple database operations
    * @return a future result of the number of rows stored to db, if supported by the driver
    */
  def processTezosAverageFees(selectionWindow: FiniteDuration)(implicit ec: ExecutionContext): Future[Option[Int]] = {
    logger.info("Processing latest Tezos fee data...")

    val computeAndStore = for {
      feeStats <- TezosDb.FeesStatistics.calculateAverage(math.max(selectionWindow.toDays, 1))
      dbWrites <- TezosDb.writeFees(feeStats.filter(fee => operationKinds.contains(fee.kind)).toList)
    } yield dbWrites

    db.run(computeAndStore).andThen {
      case Success(Some(written)) => logger.info(s"Wrote $written average fees to the database.")
      case Success(None) => logger.info("Wrote average fees to the database.")
      case Failure(e) => logger.error("Could not write average fees to the database because", e)
    }

  }

}
