package tech.cryptonomic.conseil.indexer.tezos.processing

import scala.util.{Failure, Success, Try}
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._
import cats.implicits._
import tech.cryptonomic.conseil.indexer.tezos.{TezosGovernanceOperations, TezosNamesOperations, TezosNodeOperator, Tzip16MetadataOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.tezos.{Tables, TezosTypes}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{Block, InternalOperationResults, Voting}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Syntax._
import tech.cryptonomic.conseil.indexer.config.Features
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TNSContract
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TokenContracts.Tzip16
import tech.cryptonomic.conseil.indexer.tezos.michelson.renderer.MichelsonRenderer

/** Collects operations related to handling blocks from
  * the tezos node.
  *
  * @param nodeOperator connects to tezos
  * @param db raw access to the slick database
  * @param tnsOperations module providing tns processing operations
  * @param accountsProcessor module providing entity-related operations
  * @param bakersProcessor module providing entity-related operations
  * @param tokens configured definitions of token contracts for the network
  * @param tns configured definitions of tns contract for the network
  */
class BlocksProcessor(
    nodeOperator: TezosNodeOperator,
    db: Database,
    tnsOperations: TezosNamesOperations,
    accountsProcessor: AccountsProcessor,
    bakersProcessor: BakersProcessor,
    featureFlags: Features,
    knownAddresses: Option[List[Tables.KnownAddressesRow]]
)(implicit tns: TNSContract)
    extends ConseilLogSupport {

  /* will store a single page of block results */
  private[tezos] def processBlocksPage(results: nodeOperator.BlockFetchingResults)(implicit
      ec: ExecutionContext
  ): Future[Int] = {
    def logBlockOutcome[A]: PartialFunction[Try[Option[A]], Unit] = {
      case Success(accountsCount) =>
        val showCount = accountsCount.fold("")(" " + _)
        logger.info(s"Wrote ${results.size} blocks to the database, checkpoint stored for$showCount account updates")
      case Failure(e) =>
        logger.error("Could not write blocks or accounts checkpoints to the database.", e)
    }

    //ignore the account ids for storage, and prepare the checkpoint account data
    //we do this on a single sweep over the list, pairing the results and then unzipping the outcome
    val (blocks, accountUpdates) = {
      knownAddresses match {
        case Some(value) =>
          results.map { case (block, accountIds) =>
            val ids = accountIds.toSet.intersect(value.map(x => TezosTypes.PublicKeyHash(x.address)).toSet)
             block -> accountIds.taggedWithBlockData(block.data)
          }.unzip
        case None =>
          results.map { case (block, accountIds) =>
            block -> accountIds.taggedWithBlockData(block.data)
          }.unzip
      }

    }

    for {
        _ <- db.run(TezosDb.writeBlocksAndCheckpointAccounts(blocks, accountUpdates, knownAddresses)) andThen logBlockOutcome
        _ <- tnsOperations.processNamesRegistrations(blocks).flatMap(db.run)
        bakersCheckpoints <- accountsProcessor.processAccountsForBlocks(accountUpdates) // should this fail, we still recover data from the checkpoint
        _ <- bakersProcessor.processBakersForBlocks(bakersCheckpoints)
        _ <- bakersProcessor.updateBakersBalances(blocks)
        rollsData <- nodeOperator.getBakerRollsForBlocks(blocks)
        _ <- processBlocksForGovernance(rollsData.toMap)
    } yield results.size

  }

  /** Prepares and stores statistics for voting periods of the
    * blocks passed in.
    *
    * @param bakerRollsByBlock blocks of interest, with any rolls data available
    * @return the outcome of the operation, which may fail with an error or produce no result value
    */
  private def processBlocksForGovernance(bakerRollsByBlock: Map[Block, List[Voting.BakerRolls]])(implicit
      ec: ExecutionContext
  ): Future[Unit] =
    TezosGovernanceOperations
      .extractGovernanceAggregations(db, nodeOperator)(bakerRollsByBlock)
      .flatMap(aggregates => db.run(TezosDb.insertGovernance(aggregates)))
      .void

}
