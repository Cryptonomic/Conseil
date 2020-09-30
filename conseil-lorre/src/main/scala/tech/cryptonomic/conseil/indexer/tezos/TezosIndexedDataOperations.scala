package tech.cryptonomic.conseil.indexer.tezos

import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.sql.DatabaseRunner
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{
  makeAccountId,
  AccountId,
  BlockLevel,
  BlockReference,
  PublicKeyHash,
  TezosBlockHash
}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Voting.BakerRolls
import tech.cryptonomic.conseil.common.tezos.Tables

/**
  * Functionality for fetching data from the Conseil database specific only for conseil-lorre module.
  */
private[tezos] class TezosIndexedDataOperations(
    override val dbReadHandle: Database
) extends DatabaseRunner {

  /**
    * Fetches the level of the most recent block stored in the database.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxLevel(): Future[BlockLevel] =
    runQuery(
      Tables.Blocks
        .filter(_.invalidatedAsof.isEmpty)
        .map(_.level)
        .max
        .getOrElse(defaultBlockLevel)
        .result
    )

  /**
    * Fetches the max level of baking rights.
    *
    * @return Max level or -1 if no baking rights were found in the database.
    */
  def fetchMaxBakingRightsLevel(): Future[BlockLevel] =
    runQuery(
      Tables.BakingRights
        .filter(_.invalidatedAsof.isEmpty)
        .map(_.blockLevel)
        .max
        .getOrElse(defaultBlockLevel)
        .result
    )

  /**
    * Fetches the max level of endorsing rights.
    *
    * @return Max level or -1 if no endorsing rights were found in the database.
    */
  def fetchMaxEndorsingRightsLevel(): Future[BlockLevel] =
    runQuery(
      Tables.EndorsingRights
        .filter(_.invalidatedAsof.isEmpty)
        .map(_.blockLevel)
        .max
        .getOrElse(defaultBlockLevel)
        .result
    )

  /* use as max block level when none exists */
  private[tezos] val defaultBlockLevel: BlockLevel = -1

  /**
    * Fetches a block by level from the db
    *
    * @param level the requested level for the block
    * @return the block if that level is already stored
    */
  def fetchBlockAtLevel(level: BlockLevel): Future[Option[Tables.BlocksRow]] =
    runQuery(Tables.Blocks.filter(row => row.level === level && row.invalidatedAsof.isEmpty).result.headOption)

  /** Finds activated accounts - useful when updating accounts history
    * @return sequence of activated account ids
    */
  def findActivatedAccountIds: Future[Seq[String]] =
    runQuery(
      Tables.Accounts
        .filter(acc => acc.isActivated && acc.invalidatedAsof.isEmpty)
        .map(_.accountId)
        .result
    )

  /** Gets only bakers from the accounts, excluding those for the input ids.
    *
    * @param exclude defines which bakers should be filtered out
    */
  def getFilteredBakerAccounts(
      exclude: Set[AccountId]
  )(implicit ec: ExecutionContext): Future[List[Tables.AccountsRow]] =
    runQuery(
      Tables.Accounts
        .filter(_.isBaker === true)
        .filter(_.invalidatedAsof.isEmpty)
        .filterNot(_.accountId inSet exclude.map(_.value))
        .result
        .map(_.toList)
    )

  /**
    * Gets all bakers for given block hash
    * @param hashes
    * @param ec
    * @return
    */
  def getBakersForBlocks(
      hashes: List[TezosBlockHash]
  )(implicit ec: ExecutionContext): Future[List[(TezosBlockHash, List[BakerRolls])]] =
    runQuery(DBIO.sequence {
      hashes.map { hash =>
        Tables.Bakers
          .filter(_.blockId === hash.value)
          .filter(_.invalidatedAsof.isEmpty)
          .result
          .map(hash -> _.map(baker => BakerRolls(PublicKeyHash(baker.pkh), baker.rolls)).toList)
      }
    })

  /** Fetch the latest block level available for each account id stored */
  def getLevelsForAccounts(ids: Set[AccountId]): Future[Seq[(String, BlockLevel)]] =
    runQuery(
      Tables.Accounts
        .filter(_.invalidatedAsof.isEmpty)
        .map(table => (table.accountId, table.blockLevel))
        .filter(_._1 inSet ids.map(_.value))
        .result
    )

  /** Load all operations referenced from a block level and higher, that are of a specific kind.
    * @param ofKind a set of kinds to filter operations, if empty there will be no result
    * @param fromLevel the lowest block-level to start from, zero by default
    * @return the matching operations pkh, sorted by ascending block-level
    */
  def fetchRecentOperationsHashByKind(
      ofKind: Set[String],
      fromLevel: BlockLevel = 0
  ): Future[Seq[String]] =
    runQuery(
      Tables.Operations
        .filter(
          row =>
            (row.kind inSet ofKind)
              && (row.blockLevel >= fromLevel)
              && (row.pkh.isDefined)
              && row.invalidatedAsof.isEmpty
        )
        .sortBy(_.blockLevel.asc)
        .map(_.pkh.get) //this is allowed only because we filtered by non-empty pkh
        .result
    )

  /**
    * Reads the account ids in the checkpoint table,
    * sorted by decreasing block-level
    * @return a database action that loads the list of relevant rows
    */
  def getLatestAccountsFromCheckpoint(implicit ec: ExecutionContext): Future[Map[AccountId, BlockReference]] = {
    /* Given a sorted sequence of checkpoint rows whose reference level is decreasing,
     * collects them in a map, skipping keys already added
     * This prevents duplicate entry keys and keeps the highest level referenced, using an in-memory algorithm
     * We can think of optimizing this later, we're now optimizing on db queries
     */
    def keepLatestAccountIds(checkpoints: Seq[Tables.AccountsCheckpointRow]): Map[AccountId, BlockReference] =
      checkpoints.foldLeft(Map.empty[AccountId, BlockReference]) { (collected, row) =>
        val key = makeAccountId(row.accountId)
        val time = row.asof.toInstant
        if (collected.contains(key)) collected
        else
          collected + (key -> BlockReference(TezosBlockHash(row.blockId), row.blockLevel, Some(time), row.cycle, None))
      }

    runQuery(
      Tables.AccountsCheckpoint
        .sortBy(_.blockLevel.desc)
        .result
        .map(keepLatestAccountIds)
    )
  }

}
