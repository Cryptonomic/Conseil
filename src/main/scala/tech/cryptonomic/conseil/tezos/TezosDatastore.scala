package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.Tables.{BalanceUpdatesRow, OperationGroupsRow, OperationsRow}
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  Account,
  AccountId,
  Block,
  BlockReference,
  BlockTagged,
  Delegate,
  PublicKeyHash,
  Voting
}
import tech.cryptonomic.conseil.tezos.repositories.{
  AccountsRepository,
  BlocksRepository,
  DelegatesRepository,
  OperationsRepository,
  VotingRepository
}
import tech.cryptonomic.conseil.util.Conversion

import cats.implicits._
import cats.effect._
import cats.data.Kleisli
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._
import slickeffect.implicits._
import scala.concurrent.ExecutionContext
import com.typesafe.scalalogging.LazyLogging

/**
  * Functions for writing Tezos data to a database.
  */
class TezosDatastore(
    implicit
    blocksRepo: BlocksRepository[DBIO],
    accountsRepo: AccountsRepository[DBIO],
    operationsRepo: OperationsRepository[DBIO, OperationGroupsRow, OperationsRow, Int, BalanceUpdatesRow],
    votesRepo: VotingRepository[DBIO],
    delegatesRepo: DelegatesRepository[DBIO]
) extends LazyLogging {

  /** Writes the blocks data to the database
    * at the same time saving enough information about updated accounts to later fetch those accounts
    * @param blocksWithAccounts a map with new blocks as keys, and updated account ids as the values
    * @param ec a context for async combinations
    * @return a database operation that returns counts for both added block records and accounts checkpointed
    */
  def storeBlocksAndCheckpointAccounts(
      blocksWithAccounts: Map[Block, List[AccountId]]
  )(
      implicit ec: ExecutionContext
  ): DBIO[(Option[Int], Option[Int])] = {
    import Conversion.Syntax._
    import tech.cryptonomic.conseil.tezos.BlockBalances._
    import DatabaseConversions._

    logger.info("Writing blocks and account checkpoints to the DB...")

    //ignore the account ids for storage, and prepare the checkpoint account data
    val accountUpdates =
      blocksWithAccounts.map {
        case (block, accountIds) =>
          (block.data.hash, block.data.header.level, accountIds)
      }.toList

    val blocks = blocksWithAccounts.keys.toList

    //straightforward Database IO Actions waiting to be just run
    val saveBlocks = blocksRepo.writeBlocks(blocks)

    val saveBlocksBalanceUpdates = operationsRepo.writeUpdates(
      blocks.flatMap(_.data.convertToA[List, BalanceUpdatesRow])
    )

    val saveGroups = operationsRepo.writeOperationsGroups(blocks.flatMap(_.convertToA[List, OperationGroupsRow]))

    //a function that takes a row to save and creates an action to do that, returning the new id
    val saveOperationGetNewId = Kleisli(operationsRepo.writeOperationWithNewId)

    //a function that takes rows to save with an operation id, and creates an action to do that
    val saveBalanceUpdatesForOperationId = Kleisli { updatesForOperation: (Int, List[BalanceUpdatesRow]) =>
      val (opId, updates) = updatesForOperation
      operationsRepo.writeUpdates(updates, sourceOperation = Some(opId))
    }

    /* Compose the kleisli functions to get a single "action function"
     * Calling `.first` will make the kleisli take a tuple and only apply the function to the first element
     * leaving the second untouched.
     * We do this to align the output with the input of the second step.
     *
     * Eventually it applies to a whole list of input, via the kleisli traverse operation
     * We combine the results to get a single count of everything stored, using the monoid for Option[Int]
     */
    val saveOperationsAndBalances: DBIO[Option[Int]] =
      (saveOperationGetNewId.first andThen saveBalanceUpdatesForOperationId)
        .traverse(
          blocks.flatMap(_.convertToA[List, OperationTablesData])
        )
        .map(_.combineAll)

    val saveAccountCheckpoint = accountsRepo.writeAccountsCheckpoint(accountUpdates)

    Async[DBIO]
      .tuple5(
        saveBlocks,
        saveBlocksBalanceUpdates,
        saveGroups,
        saveOperationsAndBalances,
        saveAccountCheckpoint
      )
      .transactionally
      .map {
        case (blocksStored, _, _, _, checkpointed) => (blocksStored, checkpointed)
      }

  }

  /** Reads the maximum level for a block in the db */
  def fetchMaxStoredBlockLevel: DBIO[Option[Int]] =
    blocksRepo.fetchMaxBlockLevel

  /** Reads the operations under a group */
  def fetchOperationsForGroup(groupHash: String): DBIO[Option[(OperationGroupsRow, Seq[OperationsRow])]] =
    operationsRepo.operationsForGroup(groupHash)

  /** Writes blocks-related voting details to the database
    * @param proposals the list of proposals
    * @param blockBallots the list of ballots for the associated block
    * @param blockBakers the list of rolls for bakers associated with the block
    * @param ec a context for async combinations
    * @return a database action returning the records stored, if available from the database
    */
  def storeBlocksVotingDetails(
      proposals: List[Voting.Proposal],
      blockBakers: List[(Block, List[Voting.BakerRolls])],
      blockBallots: List[(Block, List[Voting.Ballot])]
  )(
      implicit ec: ExecutionContext
  ): DBIO[Option[Int]] = {

    //this is a single list
    val saveProposals = votesRepo.writeVotingProposals(proposals)
    //this is a nested list, each block with many baker rolls
    val saveBakers = blockBakers.traverse {
      case (block, bakersRolls) => votesRepo.writeVotingRolls(bakersRolls, block)
    }
    //this is a nested list, each block with many ballot votes
    val saveBallots = blockBallots.traverse {
      case (block, ballots) => votesRepo.writeVotingBallots(ballots, block)
    }

    DBIO
      .sequence(
        List(
          saveProposals,
          saveBakers.map(_.combineAll),
          saveBallots.map(_.combineAll)
        )
      )
      .map(_.combineAll)

  }

  /** Reads the ids for accounts in the checkpoint, considering only those referencing the
    * highest block-level when there's more than one
    * @return a database action to get the ids associated with the block that referenced the account
    */
  def fetchLatestAccountsFromCheckpoint: DBIO[Map[AccountId, BlockReference]] =
    accountsRepo.getLatestAccountsFromCheckpoint

  /** Removes the account's reference from the checkpoint
    * @param ids selects a subset of the ids to remove, or clean the checkpoint if none is provided
    * @return a database action returning the records actually removed, if available from the database
    */
  def removeAccountsFromCheckpoint(ids: Option[Set[AccountId]] = None): DBIO[Int] =
    accountsRepo.cleanAccountsCheckpoint(ids)

  /** Writes accounts to the database and record the keys (hashes) to later save complete delegates information relative to each block
    * @param accounts the full accounts' data
    * @param delegatesKeyHashes for each block reference a list of pkh of delegates that were involved with the block
    * @param ec a context for async combinations
    * @return a database action that stores both arguments and return a tuple of the row counts inserted, if available from the database
    */
  def storeAccountsAndCheckpointDelegates(
      accounts: List[BlockTagged[Map[AccountId, Account]]],
      delegatesKeyHashes: List[BlockTagged[List[PublicKeyHash]]]
  )(
      implicit ec: ExecutionContext
  ): DBIO[(Int, Option[Int])] = {
    logger.info("Writing accounts and delegate checkpoints to the DB...")
    Async[DBIO]
      .tuple2(
        accountsRepo.updateAccounts(accounts),
        delegatesRepo.writeDelegatesCheckpoint(delegatesKeyHashes.map(_.asTuple))
      )
      .transactionally
  }

  /** Reads the key hashes for delegates in the checkpoint, considering only those referencing the
    * highest block-level when there's more than one
    * @return a database action to get the pkhs associated with the block that referenced the delegating contract
    */
  def fetchLatestDelegatesFromCheckpoint: DBIO[Map[PublicKeyHash, BlockReference]] =
    delegatesRepo.getLatestDelegatesFromCheckpoint

  /** Removes the delegate's reference from the checkpoint
    * @param keys selects a subset of the hashes to remove, or clean the checkpoint if none is provided
    * @return a database action returning the records actually removed, if available from the database
    */
  def removeDelegatesFromCheckpoint(keys: Option[Set[PublicKeyHash]] = None): DBIO[Int] =
    delegatesRepo.cleanDelegatesCheckpoint(keys)

  /** Writes delegates to the database and gets the delegated accounts' keys to copy the accounts data
    * as delegated contracts on the db, as a secondary copy
    * @param delegates the full delegates' data
    * @return a database action that stores delegates and returns the number of saved rows
    */
  def storeDelegatesAndCopyContracts(
      delegates: List[BlockTagged[Map[PublicKeyHash, Delegate]]]
  )(
      implicit ec: ExecutionContext
  ): DBIO[Int] = {
    logger.info("Writing delegates to DB and copying contracts to delegates contracts table...")

    val referencedContracts = for {
      BlockTagged(_, _, delegatesMap) <- delegates
      delegatesData <- delegatesMap.values
      contractIds <- delegatesData.delegated_contracts
    } yield contractIds

    (delegatesRepo.updateDelegates(delegates) <* delegatesRepo.copyAccountsAsDelegateContracts(
          referencedContracts.toSet
        )).transactionally

  }

}
