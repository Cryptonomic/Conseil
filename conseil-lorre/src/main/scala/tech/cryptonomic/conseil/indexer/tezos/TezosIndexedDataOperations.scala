package tech.cryptonomic.conseil.indexer.tezos

import scala.concurrent.Future
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.sql.DatabaseMetadataOperations
import tech.cryptonomic.conseil.common.tezos.TezosTypes.BlockLevel
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.util.DatabaseUtil

/**
  * Functionality for fetching data from the Conseil database specific only for conseil-lorre module.
  */
private[tezos] class TezosIndexedDataOperations extends DatabaseMetadataOperations {
  override lazy val dbReadHandle: Database = DatabaseUtil.lorreDb

  /**
    * Fetches the level of the most recent block stored in the database.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxLevel(): Future[BlockLevel] =
    runQuery(
      Tables.Blocks
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
    runQuery(Tables.BakingRights.map(_.blockLevel).max.getOrElse(defaultBlockLevel).result)

  /**
    * Fetches the max level of endorsing rights.
    *
    * @return Max level or -1 if no endorsing rights were found in the database.
    */
  def fetchMaxEndorsingRightsLevel(): Future[BlockLevel] =
    runQuery(Tables.EndorsingRights.map(_.blockLevel).max.getOrElse(defaultBlockLevel).result)

  /* use as max block level when none exists */
  private[tezos] val defaultBlockLevel: BlockLevel = -1

  /**
    * Fetches a block by level from the db
    *
    * @param level the requested level for the block
    * @return the block if that level is already stored
    */
  def fetchBlockAtLevel(level: BlockLevel): Future[Option[Tables.BlocksRow]] =
    runQuery(Tables.Blocks.filter(_.level === level).result.headOption)

}
