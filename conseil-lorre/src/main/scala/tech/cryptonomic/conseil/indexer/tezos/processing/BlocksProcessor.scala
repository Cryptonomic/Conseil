package tech.cryptonomic.conseil.indexer.tezos.processing

import com.typesafe.scalalogging.LazyLogging
import scala.util.{Failure, Success, Try}
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._
import cats.implicits._

import tech.cryptonomic.conseil.indexer.tezos.{
  TezosNamesOperations,
  TezosNodeOperator,
  TezosGovernanceOperations,
  TezosDatabaseOperations => TezosDb
}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{Block, Voting}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Syntax._
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TNSContract

class BlocksProcessor(
    nodeOperator: TezosNodeOperator,
    db: Database,
    tnsOperations: TezosNamesOperations,
    accountsProcessor: AccountsProcessor,
    bakersProcessor: BakersProcessor
)(implicit tokens: TokenContracts, tns: TNSContract)
    extends LazyLogging {

  /* will store a single page of block results */
  private[tezos] def processBlocksPage(results: nodeOperator.BlockFetchingResults)(
      implicit ec: ExecutionContext
  ): Future[Int] = {
    def logBlockOutcome[A]: PartialFunction[Try[Option[A]], Unit] = {
      case Success(accountsCount) =>
        logger.info(
          "Wrote {} blocks to the database, checkpoint stored for{} account updates",
          results.size,
          accountsCount.fold("")(" " + _)
        )
      case Failure(e) =>
        logger.error("Could not write blocks or accounts checkpoints to the database.", e)
    }

    //ignore the account ids for storage, and prepare the checkpoint account data
    //we do this on a single sweep over the list, pairing the results and then unzipping the outcome
    val (blocks, accountUpdates) =
      results.map {
        case (block, accountIds) =>
          block -> accountIds.taggedWithBlockData(block.data)
      }.unzip

    for {
      _ <- db.run(TezosDb.writeBlocksAndCheckpointAccounts(blocks, accountUpdates)) andThen logBlockOutcome
      _ <- tnsOperations.processNamesRegistrations(blocks).flatMap(db.run)
      rollsData <- nodeOperator.getBakerRollsForBlocks(blocks)
      rollsByBlockHash = rollsData.map { case (block, rolls) => block.data.hash -> rolls }.toMap
      bakersCheckpoints <- accountsProcessor.processAccountsForBlocks(accountUpdates, rollsByBlockHash) // should this fail, we still recover data from the checkpoint
      _ <- bakersProcessor.processBakersForBlocks(bakersCheckpoints)
      _ <- bakersProcessor.updateBakersBalances(blocks)
      _ <- processBlocksForGovernance(rollsData.toMap)
    } yield results.size

  }

  /** Prepares and stores statistics for voting periods of the
    * blocks passed in.
    *
    * @param bakerRollsByBlock blocks of interest, with any rolls data available
    * @return the outcome of the operation, which may fail with an error or produce no result value
    */
  private def processBlocksForGovernance(bakerRollsByBlock: Map[Block, List[Voting.BakerRolls]])(
      implicit ec: ExecutionContext
  ): Future[Unit] =
    TezosGovernanceOperations
      .extractGovernanceAggregations(db, nodeOperator)(bakerRollsByBlock)
      .flatMap(aggregates => db.run(TezosDb.insertGovernance(aggregates)))
      .void

}
