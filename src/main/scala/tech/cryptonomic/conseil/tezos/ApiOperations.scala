package tech.cryptonomic.conseil.tezos

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos
import tech.cryptonomic.conseil.tezos.Tables.AccountsRow
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

/**
  * Functionality for fetching data from the Conseil database.
  */
object ApiOperations {

  lazy val dbHandle: Database = DatabaseUtil.db

  case class Filter(
                     limit: Option[Int] = Some(10),
                     blockIDs: Option[Set[String]] = Some(Set[String]()),
                     levels: Option[Set[Int]] = Some(Set[Int]()),
                     netIDs: Option[Set[String]] = Some(Set[String]()),
                     protocols: Option[Set[String]] = Some(Set[String]()),
                     operationIDs: Option[Set[String]] = Some(Set[String]()),
                     operationSources: Option[Set[String]] = Some(Set[String]()),
                     accountIDs: Option[Set[String]] = Some(Set[String]()),
                     accountManagers: Option[Set[String]] = Some(Set[String]()),
                     accountDelegates: Option[Set[String]] = Some(Set[String]()),
                   )

  private def filterBlockIDs(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.blockIDs.isDefined && filter.blockIDs.get.nonEmpty) b.hash.inSet(filter.blockIDs.get) else true

  private def filterBlockLevels(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.levels.isDefined && filter.levels.get.nonEmpty) b.level.inSet(filter.levels.get) else true

  private def filterNetIDs(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.netIDs.isDefined && filter.netIDs.get.nonEmpty) b.netId.inSet(filter.netIDs.get) else true

  private def filterProtocols(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.protocols.isDefined && filter.protocols.get.nonEmpty) b.protocol.inSet(filter.protocols.get) else true

  private def filterOperationIDs(filter: Filter, op: Tables.OperationGroups): Rep[Boolean] =
    if (filter.operationIDs.isDefined && filter.operationIDs.get.nonEmpty) op.hash.inSet(filter.operationIDs.get) else true

  private def filterOperationSources(filter: Filter, op: Tables.OperationGroups): Rep[Boolean] =
    if (filter.operationSources.isDefined && filter.operationSources.get.nonEmpty) op.source.get.inSet(filter.operationSources.get) else true

  private def filterAccountIDs(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
    if (filter.accountIDs.isDefined && filter.accountIDs.get.nonEmpty) a.accountId.inSet(filter.accountIDs.get) else true

  private def filterAccountManagers(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
    if (filter.accountManagers.isDefined && filter.accountManagers.get.nonEmpty) a.manager.inSet(filter.accountManagers.get) else true

  private def filterAccountDelegates(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
    if (filter.accountDelegates.isDefined && filter.accountDelegates.get.nonEmpty) a.delegateValue.get.inSet(filter.accountDelegates.get) else true

  private def getFilterLimit(filter: Filter): Int = if (filter.limit.isDefined) filter.limit.get else 10

  /**
    * Fetches the level of the most recent block stored in the database.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxLevel(): Try[Int] = Try {
    val op: Future[Option[Int]] = dbHandle.run(Tables.Blocks.map(_.level).max.result)
    val maxLevelOpt = Await.result(op, Duration.Inf)
    maxLevelOpt match {
      case Some(maxLevel) => maxLevel
      case None => -1
    }
  }

  /**
    * Fetches the most recent block stored in the database.
    *
    * @return Latest block.
    */
  def fetchLatestBlock(): Try[Tables.BlocksRow] = {
    fetchMaxLevel().flatMap { maxLevel =>
      Try {
        val op: Future[Seq[tezos.Tables.BlocksRow]] = dbHandle.run(Tables.Blocks.filter(_.level === maxLevel).take(1).result)
        Await.result(op, Duration.Inf).head
      }
    }
  }

  /**
    * Fetches a block by block hash from the db.
    * @param hash the block's hash
    * @return block
    */
  def fetchBlock(hash: String): Try[Tables.BlocksRow] = Try {
    val op = dbHandle.run(Tables.Blocks.filter(_.hash === hash).take(1).result)
    Await.result(op, Duration.Inf).head
  }

  /**
    * Fetches all blocks from the db.
    *
    * @return list of blocks
    */
  def fetchBlocks(filter: Filter): Try[Seq[Tables.BlocksRow]] = Try {
        val action = for {
          b: Tables.Blocks <- Tables.Blocks
          og <- Tables.OperationGroups
          if b.hash === og.blockId &&
          filterBlockIDs(filter, b) &&
          filterBlockLevels(filter, b) &&
          filterNetIDs(filter, b) &&
          filterProtocols(filter, b) &&
          filterOperationIDs(filter, og) &&
          filterOperationSources(filter, og)
        } yield (b.netId, b.protocol, b.level, b.proto, b.predecessor, b.validationPass, b.operationsHash, b.data, b.hash, b.timestamp, b.fitness)
        val op = dbHandle.run(action.distinct.take(getFilterLimit(filter)).result)
        val results = Await.result(op, Duration.Inf)
        results.map(x => Tables.BlocksRow(x._1, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9, x._10, x._11))
      }

  //def fetchOperationGroup(operationGroup_id: String): Try[Tables.OperationGroupsRow] = {
  //
  //}

  /**
    * Fetches an account by account_id from the db.
    *
    * @param account_id the account's id number
    * @return account
    */
  def fetchAccount(account_id: String): Try[Tables.AccountsRow] =
    fetchLatestBlock().flatMap { latestBlock =>
      Try {
        val op: Future[Seq[tezos.Tables.AccountsRow]] = dbHandle.run(Tables.Accounts
          .filter(_.blockId === latestBlock.hash)
          .filter(_.accountId === account_id).take(1).result)
        Await.result(op, Duration.Inf).head
      }
    }

  /**
    * Fetches a list of accounts from the db.
    *
    * @return list of accounts
    */
  def fetchAccounts(filter: Filter): Try[Seq[AccountsRow]] =
    fetchLatestBlock().flatMap { latestBlock =>
      Try {
        val action = for {
          a: Tables.Accounts <- Tables.Accounts
          if a.blockId === latestBlock.hash &&
          filterAccountIDs(filter, a) &&
          filterAccountDelegates(filter, a) &&
          filterAccountManagers(filter, a)
        } yield (a.accountId, a.blockId, a.manager, a.spendable, a.delegateSetable, a.delegateValue, a.counter)
        val op = dbHandle.run(action.distinct.take(getFilterLimit(filter)).result)
        val results = Await.result(op, Duration.Inf)
        results.map(x => Tables.AccountsRow(x._1, x._2, x._3, x._4, x._5, x._6, x._7))
      }
    }

}

