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
    if (filter.operationSources.isDefined && filter.operationSources.get.nonEmpty)
      op.source.getOrElse("").inSet(filter.operationSources.get)
    else true

  private def filterAccountIDs(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
    if (filter.accountIDs.isDefined && filter.accountIDs.get.nonEmpty) a.accountId.inSet(filter.accountIDs.get) else true

  private def filterAccountManagers(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
    if (filter.accountManagers.isDefined && filter.accountManagers.get.nonEmpty) a.manager.inSet(filter.accountManagers.get) else true

  private def filterAccountDelegates(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
    if (filter.accountDelegates.isDefined && filter.accountDelegates.get.nonEmpty)
      a.delegateValue.getOrElse("").inSet(filter.accountDelegates.get)
    else true

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
  def fetchBlock(hash: String): Try[Map[String, Any]] = Try {
    val op = dbHandle.run(Tables.Blocks.filter(_.hash === hash).take(1).result)
    val block = Await.result(op, Duration.Inf).head
    val op2 = dbHandle.run(Tables.OperationGroups.filter(_.blockId === hash).result)
    val operationGroups = Await.result(op2, Duration.Inf)
    Map("block" -> block, "operation_groups" -> operationGroups)
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

  def fetchOperationGroup(operationGroupHash: String) : Try[Map[String, Any]] = Try {
    val op = dbHandle.run(Tables.OperationGroups.filter(_.hash === operationGroupHash).take(1).result)
    val opGroup = Await.result(op, Duration.Inf).head
    val op2 = dbHandle.run(Tables.Transactions.filter(_.operationGroupHash === operationGroupHash).result)
    val transactions = Await.result(op2, Duration.Inf)
    val op3 = dbHandle.run(Tables.Originations.filter(_.operationGroupHash === operationGroupHash).result)
    val originations = Await.result(op3, Duration.Inf)
    val op4 = dbHandle.run(Tables.Delegations.filter(_.operationGroupHash === operationGroupHash).result)
    val delegations = Await.result(op4, Duration.Inf)
    val op5 = dbHandle.run(Tables.Endorsements.filter(_.operationGroupHash === operationGroupHash).result)
    val endorsements = Await.result(op5, Duration.Inf)
    val op6 = dbHandle.run(Tables.Proposals.filter(_.operationGroupHash === operationGroupHash).result)
    val proposals = Await.result(op6, Duration.Inf)
    val op7 = dbHandle.run(Tables.Ballots.filter(_.operationGroupHash === operationGroupHash).result)
    val ballots = Await.result(op7, Duration.Inf)
    val op8 = dbHandle.run(Tables.SeedNonceRevealations.filter(_.operationGroupHash === operationGroupHash).result)
    val seedNonceRevealations = Await.result(op8, Duration.Inf)
    val op9 = dbHandle.run(Tables.FaucetTransactions.filter(_.operationGroupHash === operationGroupHash).result)
    val faucetTransactions = Await.result(op9, Duration.Inf)
    Map(
      "operation_group"         -> opGroup,
      "transactions"            -> transactions,
      "originations"            -> originations,
      "delegations"             -> delegations,
      "endorsements"            -> endorsements,
      "proposals"               -> proposals,
      "ballots"                 -> ballots,
      "seed_nonce_revelations"  -> seedNonceRevealations,
      "faucet_transactions"     -> faucetTransactions
    )
  }

  def fetchOperationGroups(filter: Filter): Try[Seq[Tables.OperationGroupsRow]] = Try {
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
    } yield (og.hash, og.blockId, og.branch, og.source, og.signature)
    val op = dbHandle.run(action.distinct.take(getFilterLimit(filter)).result)
    val results = Await.result(op, Duration.Inf)
    results.map(x => Tables.OperationGroupsRow(x._1, x._2, x._3, x._4, x._5))
  }

  /**
    * Fetches an account by account_id from the db.
    *
    * @param account_id the account's id number
    * @return account
    */
  def fetchAccount(account_id: String): Try[Map[String, Any]] =
    fetchLatestBlock().flatMap { latestBlock =>
      Try {
        val op = dbHandle.run(Tables.Accounts
          .filter(_.blockId === latestBlock.hash)
          .filter(_.accountId === account_id).take(1).result)
        val account = Await.result(op, Duration.Inf).head
        val op2 = dbHandle.run(Tables.OperationGroups.filter(_.source === account_id).result)
        val operationGroups = Await.result(op2, Duration.Inf)
        Map("account" -> account, "operation_groups" -> operationGroups)
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

