package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import slick.dbio.DBIO
//import tech.cryptonomic.conseil.Lorre.db
import tech.cryptonomic.conseil.tezos.repositories.FeesRepository
import scala.concurrent.ExecutionContext
// import scala.util.{Failure, Success}

/**
  * Helper classes and functions used for average fee calculations.
  */
class FeeOperations(implicit repo: FeesRepository[DBIO]) extends LazyLogging {

  private val operationKinds: List[Fees.OperationKind] = List(
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
  def processTezosAverageFees(numberOfFeesAveraged: Int)(implicit ex: ExecutionContext): DBIO[Option[Int]] =
    for {
      fees <- DBIO.sequence(operationKinds.map(repo.calculateAverageFees(_, numberOfFeesAveraged)))
      dbWrites <- repo.writeFees(fees.collect { case Some(fee) => fee })
    } yield dbWrites

}
