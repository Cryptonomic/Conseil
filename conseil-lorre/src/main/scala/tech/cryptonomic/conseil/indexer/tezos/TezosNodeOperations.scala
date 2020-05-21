package tech.cryptonomic.conseil.indexer.tezos

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.sql.DatabaseMetadataOperations
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.util.DatabaseUtil

import scala.concurrent.{ExecutionContext, Future}

/**
  * Functionality for fetching data from the Conseil database specific only for conseil-lorre module.
  */
private[tezos] class TezosNodeOperations extends DatabaseMetadataOperations {
  override lazy val dbReadHandle: Database = DatabaseUtil.lorreDb

  /**
    * Fetches the level of the most recent block stored in the database.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxLevel()(implicit ec: ExecutionContext): Future[Int] = {
    val optionalMax: Future[Option[Int]] = runQuery(Tables.Blocks.map(_.level).max.result)
    optionalMax.map(_.getOrElse(-1))
  }

  /**
    * Fetches the max level of baking rights.
    *
    * @return Max level or -1 if no baking rights were found in the database.
    */
  def fetchMaxBakingRightsLevel()(implicit ec: ExecutionContext): Future[Int] = {
    val optionalMax: Future[Option[Int]] = runQuery(Tables.BakingRights.map(_.level).max.result)
    optionalMax.map(_.getOrElse(-1))
  }

  /**
    * Fetches the max level of endorsing rights.
    *
    * @return Max level or -1 if no endorsing rights were found in the database.
    */
  def fetchMaxEndorsingRightsLevel()(implicit ec: ExecutionContext): Future[Int] = {
    val optionalMax: Future[Option[Int]] = runQuery(Tables.EndorsingRights.map(_.level).max.result)
    optionalMax.map(_.getOrElse(-1))
  }

  /**
    * Fetches a block by level from the db
    *
    * @param level the requested level for the block
    * @return the block if that level is already stored
    */
  def fetchBlockAtLevel(level: Int): Future[Option[Tables.BlocksRow]] =
    runQuery(Tables.Blocks.filter(_.level === level).result.headOption)

}
