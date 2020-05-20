package tech.cryptonomic.conseil.indexer.tezos

import com.typesafe.scalalogging.LazyLogging
import slick.dbio.DBIOAction
import tech.cryptonomic.conseil.common.tezos.{TezosDatabaseOperations => TezosDb}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Helper classes and functions used for average fee calculations.
  */
private[tezos] object TezosFeeOperations extends LazyLogging {
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

  /**
    * Calculates average fees for each operation kind and stores them into a fees table.
    * @param numberOfFeesAveraged a limit on how many of the latest fee values will be used for averaging
    * @param ex the needed ExecutionContext to combine multiple database operations
    * @return a future result of the number of rows stored to db, if supported by the driver
    */
  def processTezosAverageFees(numberOfFeesAveraged: Int)(implicit ex: ExecutionContext): Future[Option[Int]] = {
    logger.info("Processing latest Tezos fee data...")
    val computeAndStore = for {
      fees <- DBIOAction.sequence(operationKinds.map(TezosDb.calculateAverageFees(_, numberOfFeesAveraged)))
      dbWrites <- TezosDb.writeFees(fees.collect { case Some(fee) => fee })
    } yield dbWrites

    db.run(computeAndStore).andThen {
      case Success(Some(written)) => logger.info("Wrote {} average fees to the database.", written)
      case Success(None) => logger.info("Wrote average fees to the database.")
      case Failure(e) => logger.error("Could not write average fees to the database because", e)
    }

  }

}
