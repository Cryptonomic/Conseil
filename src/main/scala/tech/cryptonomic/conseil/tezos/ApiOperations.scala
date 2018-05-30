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

  /**
    * Repesents a query filter submitted to the Conseil API.
    *
    * @param limit            How many records to return
    * @param blockIDs         Block IDs
    * @param levels           Block levels
    * @param chainIDs         Chain IDs
    * @param protocols        Protocols
    * @param operationIDs     Operation IDs
    * @param operationSources Operation sources
    * @param accountIDs       Account IDs
    * @param accountManagers  Account managers
    * @param accountDelegates Account delegates
    */
  case class Filter(
                     limit: Option[Int] = Some(10),
                     blockIDs: Option[Set[String]] = Some(Set[String]()),
                     levels: Option[Set[Int]] = Some(Set[Int]()),
                     chainIDs: Option[Set[String]] = Some(Set[String]()),
                     protocols: Option[Set[String]] = Some(Set[String]()),
                     operationIDs: Option[Set[String]] = Some(Set[String]()),
                     operationSources: Option[Set[String]] = Some(Set[String]()),
                     accountIDs: Option[Set[String]] = Some(Set[String]()),
                     accountManagers: Option[Set[String]] = Some(Set[String]()),
                     accountDelegates: Option[Set[String]] = Some(Set[String]()),
                     operationKinds: Option[Set[String]] = Some(Set[String]()),
                     operationGroupKinds: Option[Set[String]] = Some(Set[String]())
                     //sortBy: Option[String] Column name
                     //order: Option[String] DESC/ASC
                   )

  case class OperationGroupFilterFlag(exists: Boolean)

  case class OperationFilterFlag(exists: Boolean)

  case class AccountFilterFlag(exists: Boolean)

  case class FilteredTables(
                             filteredAccounts: Query[Tables.Accounts, Tables.Accounts#TableElementType, Seq],
                             filteredBlocks: Query[Tables.Blocks, Tables.Blocks#TableElementType, Seq],
                             filteredOperationGroups: Query[Tables.OperationGroups, Tables.OperationGroups#TableElementType, Seq],
                             filteredOperations: Query[Tables.Operations, Tables.Operations#TableElementType, Seq]
                           )

  sealed trait JoinedTables

  case class BlocksOperationGroupsOperationsAccounts
  (table: Query[(((Tables.Blocks, Tables.OperationGroups), Tables.Operations), Tables.Accounts),
    (((Tables.Blocks#TableElementType, Tables.OperationGroups#TableElementType),
      Tables.Operations#TableElementType), Tables.Accounts#TableElementType),
    Seq]) extends JoinedTables

  case class BlocksOperationGroupsOperations
  (table: Query[((Tables.Blocks, Tables.OperationGroups), Tables.Operations),
    ((Tables.Blocks#TableElementType, Tables.OperationGroups#TableElementType),
      Tables.Operations#TableElementType),
    Seq]) extends JoinedTables

  case class BlocksOperationGroupsAccounts
  (table: Query[((Tables.Blocks, Tables.OperationGroups), Tables.Accounts),
    ((Tables.Blocks#TableElementType, Tables.OperationGroups#TableElementType),
      Tables.Accounts#TableElementType),
    Seq]) extends JoinedTables

  case class BlocksOperationGroups
  (table: Query[(Tables.Blocks, Tables.OperationGroups),
    (Tables.Blocks#TableElementType, Tables.OperationGroups#TableElementType),
    Seq]) extends JoinedTables

  case class BlocksOperationsAccounts
  (table: Query[((Tables.Blocks, Tables.Operations), Tables.Accounts),
    ((Tables.Blocks#TableElementType, Tables.Operations#TableElementType),
      Tables.Accounts#TableElementType),
    Seq]) extends JoinedTables

  case class BlocksOperations
  (table: Query[(Tables.Blocks, Tables.Operations),
    (Tables.Blocks#TableElementType, Tables.Operations#TableElementType),
    Seq]) extends JoinedTables

  case class BlocksAccounts
  (table: Query[(Tables.Blocks, Tables.Accounts),
    (Tables.Blocks#TableElementType, Tables.Accounts#TableElementType),
    Seq]) extends JoinedTables

  case class Blocks
  (table: Query[Tables.Blocks, Tables.Blocks#TableElementType, Seq]) extends JoinedTables

  case class OperationGroupsOperationsAccounts
  (table: Query[((Tables.OperationGroups, Tables.Operations), Tables.Accounts),
    ((Tables.OperationGroups#TableElementType, Tables.Operations#TableElementType),
      Tables.Accounts#TableElementType),
    Seq]) extends JoinedTables

  case class OperationGroupsOperations
  (table: Query[(Tables.OperationGroups, Tables.Operations),
    (Tables.OperationGroups#TableElementType, Tables.Operations#TableElementType),
    Seq]) extends JoinedTables

  case class OperationGroupsAccounts
  (table: Query[(Tables.OperationGroups, Tables.Accounts),
    (Tables.OperationGroups#TableElementType, Tables.Accounts#TableElementType),
    Seq]) extends JoinedTables

  case class OperationGroups
  (table: Query[Tables.OperationGroups, Tables.OperationGroups#TableElementType, Seq]) extends JoinedTables

  case class OperationsAccounts
  (table: Query[(Tables.Operations, Tables.Accounts),
    (Tables.Operations#TableElementType, Tables.Accounts#TableElementType),
    Seq]) extends JoinedTables

  case class Operations
  (table: Query[Tables.Operations, Tables.Operations#TableElementType, Seq]) extends JoinedTables

  case class Accounts
  (table: Query[Tables.Accounts, Tables.Accounts#TableElementType, Seq]) extends JoinedTables

  // Predicates to determine existence of specific type of filter

  private def isBlockFilter(filter: Filter): Boolean =
    (filter.blockIDs.isDefined && filter.blockIDs.get.nonEmpty) ||
      (filter.levels.isDefined && filter.levels.get.nonEmpty) ||
      (filter.chainIDs.isDefined && filter.chainIDs.get.nonEmpty) ||
      (filter.protocols.isDefined && filter.protocols.get.nonEmpty)

  private def isOperationGroupFilter(filter: Filter): Boolean =
    (filter.operationIDs.isDefined && filter.operationIDs.get.nonEmpty) ||
      (filter.operationSources.isDefined && filter.operationSources.get.nonEmpty) ||
      (filter.operationGroupKinds.isDefined && filter.operationGroupKinds.get.nonEmpty)

  private def isOperationFilter(filter: Filter): Boolean =
    filter.operationKinds.isDefined && filter.operationKinds.get.nonEmpty

  private def isAccountFilter(filter: Filter): Boolean =
    (filter.accountDelegates.isDefined && filter.accountDelegates.get.nonEmpty) ||
      (filter.accountIDs.isDefined && filter.accountIDs.get.nonEmpty) ||
      (filter.accountManagers.isDefined && filter.accountManagers.get.nonEmpty)

  private def isOpGroupOrOpOrAccountFilter(filter: Filter): (OperationGroupFilterFlag, OperationFilterFlag, AccountFilterFlag) =
    (OperationGroupFilterFlag(isOperationGroupFilter(filter)), OperationFilterFlag(isOperationFilter(filter)),
      AccountFilterFlag(isAccountFilter(filter)))

  // Start helper functions for constructing Slick queries

  private def filterBlockIDs(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.blockIDs.isDefined && filter.blockIDs.get.nonEmpty) b.hash.inSet(filter.blockIDs.get) else true

  private def filterBlockLevels(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.levels.isDefined && filter.levels.get.nonEmpty) b.level.inSet(filter.levels.get) else true

  private def filterChainIDs(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.chainIDs.isDefined && filter.chainIDs.get.nonEmpty) b.chainId.inSet(filter.chainIDs.get) else true

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

  private def filterOperationGroupKinds(filter: Filter, op: Tables.OperationGroups): Rep[Boolean] =
    if (filter.operationGroupKinds.isDefined && filter.operationGroupKinds.get.nonEmpty)
      op.kind.getOrElse("").inSet(filter.operationGroupKinds.get) else true

  private def filterOperationKinds(filter: Filter, op: Tables.Operations): Rep[Boolean] =
    if (filter.operationKinds.isDefined && filter.operationKinds.get.nonEmpty)
      op.opKind.inSet(filter.operationKinds.get) else true

  private def getFilterLimit(filter: Filter): Int = if (filter.limit.isDefined) filter.limit.get else 10

  // End helper functions for constructing Slick queries

  private def getFilteredTables(filter: Filter): Try[FilteredTables] = {
    fetchLatestBlock().flatMap { latestBlock =>
      Try {
        val filteredAccounts =
          Tables.Accounts.filter({ account =>
            filterAccountIDs(filter, account) &&
              filterAccountDelegates(filter, account) &&
              filterAccountManagers(filter, account) &&
              account.blockId === latestBlock.hash
          })
        val filteredOpGroups = Tables.OperationGroups.filter({ opGroup =>
          filterOperationIDs(filter, opGroup) &&
            filterOperationSources(filter, opGroup) &&
            filterOperationGroupKinds(filter, opGroup)
        })
        val filteredOps = Tables.Operations.filter({ op => filterOperationKinds(filter, op) })
        val filteredBlocks = Tables.Blocks.filter({ block =>
          filterBlockIDs(filter, block) &&
            filterBlockLevels(filter, block) &&
            filterChainIDs(filter, block) &&
            filterProtocols(filter, block)
        })
        FilteredTables(filteredAccounts, filteredBlocks, filteredOpGroups, filteredOps)
      }
    }
  }

  private def getJoinedTables(isBlockFilter: Boolean,
                              isOperationGroupFilter: Boolean,
                              isOperationFilter: Boolean,
                              isAccountFilter: Boolean,
                              tables: FilteredTables): Option[JoinedTables] = {
    val blocks = tables.filteredBlocks
    val operationGroups = tables.filteredOperationGroups
    val operations = tables.filteredOperations
    val accounts = tables.filteredAccounts
    (isBlockFilter, isOperationGroupFilter, isOperationFilter, isAccountFilter) match {
      case (true, true, true, true) =>
        Some(BlocksOperationGroupsOperationsAccounts(
          blocks
            .join(operationGroups).on(_.hash === _.blockId)
            .join(operations).on(_._2.hash === _.operationGroupHash)
            .join(accounts).on(_._1._2.source === _.accountId)))
      case (true, true, true, false) =>
        Some(BlocksOperationGroupsOperations(
          blocks
            .join(operationGroups).on(_.hash === _.blockId)
            .join(operations).on(_._2.hash === _.operationGroupHash)))
      case (true, true, false, true) =>
        None
      case (true, true, false, false) =>
        Some(BlocksOperationGroups(
          blocks.join(operationGroups).on(_.hash === _.blockId)))
      case (true, false, true, true) =>
        None
      case (true, false, true, false) =>
        None
      case (true, false, false, true) =>
        None
      case (true, false, false, false) =>
        Some(Blocks(blocks))
      case (false, true, true, true) =>
        Some(OperationGroupsOperationsAccounts(
          operationGroups
            .join(operations).on(_.hash === _.operationGroupHash)
            .join(accounts).on(_._1.source === _.accountId)))
      case (false, true, true, false) =>
        Some(OperationGroupsOperations(
          operationGroups.join(operations).on(_.hash === _.operationGroupHash)))
      case (false, true, false, true) =>
        Some(OperationGroupsAccounts(
          operationGroups.join(accounts).on(_.source === _.accountId)))
      case (false, true, false, false) =>
        Some(OperationGroups(operationGroups))
      case (false, false, true, true) =>
        None
      case (false, false, true, false) =>
        None
      case (false, false, false, true) =>
        Some(Accounts(accounts))
    }
  }

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
    *
    * @param hash The block's hash
    * @return The block along with its operations
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
    * @param filter Filters to apply
    * @return List of blocks
    */
  def fetchBlocks(filter: Filter): Try[Seq[Tables.BlocksRow]] =

    getFilteredTables(filter).flatMap { filteredTables =>
      Try {
        val blockFlag = true
        val operationGroupFlag = isOperationGroupFilter(filter)
        val operationFlag = isOperationFilter(filter)
        val accountFlag = isAccountFilter(filter)
        val joinedTables = getJoinedTables(blockFlag, operationGroupFlag, operationFlag, accountFlag, filteredTables)
        val action = joinedTables match {

          case Some(Blocks(blocks)) =>
            for {
              b <- blocks
            } yield (b.chainId, b.protocol, b.level, b.proto, b.predecessor, b.validationPass, b.operationsHash, b.protocolData, b.hash, b.timestamp, b.fitness)

          case Some(BlocksOperationGroups(blocksOperationGroups)) =>
            for {
              (b, _) <- blocksOperationGroups
            } yield (b.chainId, b.protocol, b.level, b.proto, b.predecessor, b.validationPass, b.operationsHash, b.protocolData, b.hash, b.timestamp, b.fitness)

          case Some(BlocksOperationGroupsOperations(blocksOperationGroupsOperations)) =>
            for {
              ((b, _), _) <- blocksOperationGroupsOperations
            } yield (b.chainId, b.protocol, b.level, b.proto, b.predecessor, b.validationPass, b.operationsHash, b.protocolData, b.hash, b.timestamp, b.fitness)

          case None =>
            throw new Exception("")
        }

        // sort by level
        val sortedAction = action.sortBy(_._3.desc)
        val op = dbHandle.run(sortedAction.distinct.take(getFilterLimit(filter)).result)
        val results = Await.result(op, Duration.Inf)
        results.map(x => Tables.BlocksRow(x._1, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9, x._10, x._11))
      }
    }

  /**
    * Fetch a given operation group
    *
    * @param operationGroupHash Operation group hash
    * @return Operation group along with associated operations and accounts
    */
  def fetchOperationGroup(operationGroupHash: String): Try[Map[String, Any]] =
    fetchLatestBlock().flatMap { latestBlock =>
      Try {
        val op = dbHandle.run(Tables.OperationGroups.filter(_.hash === operationGroupHash).take(1).result)
        val opGroup = Await.result(op, Duration.Inf).head
        val op2 = dbHandle.run(Tables.Operations.filter(_.operationGroupHash === operationGroupHash).result)
        val operations = Await.result(op2, Duration.Inf)
        val op3 = dbHandle.run(Tables.Accounts.
          filter(_.accountId === opGroup.source.getOrElse("")).
          filter(_.blockId === latestBlock.hash).
          result)
        val accounts = Await.result(op3, Duration.Inf)
        Map(
          "operation_group" -> opGroup,
          "operations" -> operations,
          "accounts" -> accounts
        )
      }
    }

  /**
    * Fetches all operation groups.
    *
    * @param filter Filters to apply
    * @return List of operation groups
    */
  def fetchOperationGroups(filter: Filter): Try[Seq[Tables.OperationGroupsRow]] =

    getFilteredTables(filter).flatMap { filteredTables =>
      Try {
        val blockFlag = isBlockFilter(filter)
        val operationGroupFlag = true
        val operationFlag = isOperationFilter(filter)
        val accountFlag = isAccountFilter(filter)
        val joinedTables = getJoinedTables(blockFlag, operationGroupFlag, operationFlag, accountFlag, filteredTables)
        val action = joinedTables match {

          case Some(OperationGroups(operationGroups)) =>
            for {
              opGroups <- operationGroups
            } yield (opGroups.hash, opGroups.branch, opGroups.kind, opGroups.block, opGroups.level,
              opGroups.slots, opGroups.signature, opGroups.proposals, opGroups.period, opGroups.source,
              opGroups.proposal, opGroups.ballot, opGroups.chain, opGroups.counter, opGroups.fee, opGroups.blockId)

          case Some(BlocksOperationGroups(blocksOperationGroups)) =>
            for {
              (_, opGroups) <- blocksOperationGroups
            } yield (opGroups.hash, opGroups.branch, opGroups.kind, opGroups.block, opGroups.level,
              opGroups.slots, opGroups.signature, opGroups.proposals, opGroups.period, opGroups.source,
              opGroups.proposal, opGroups.ballot, opGroups.chain, opGroups.counter, opGroups.fee, opGroups.blockId)

          case Some(OperationGroupsOperations(operationGroupsOperations)) =>
            for {
              (opGroups, _) <- operationGroupsOperations
            } yield (opGroups.hash, opGroups.branch, opGroups.kind, opGroups.block, opGroups.level,
              opGroups.slots, opGroups.signature, opGroups.proposals, opGroups.period, opGroups.source,
              opGroups.proposal, opGroups.ballot, opGroups.chain, opGroups.counter, opGroups.fee, opGroups.blockId)

          case Some(OperationGroupsAccounts(operationGroupsAccounts)) =>
            for {
              (opGroups, _) <- operationGroupsAccounts
            } yield (opGroups.hash, opGroups.branch, opGroups.kind, opGroups.block, opGroups.level,
              opGroups.slots, opGroups.signature, opGroups.proposals, opGroups.period, opGroups.source,
              opGroups.proposal, opGroups.ballot, opGroups.chain, opGroups.counter, opGroups.fee, opGroups.blockId)

          case Some(OperationGroupsOperationsAccounts(operationGroupsOperationsAccounts)) =>
            for {
              ((opGroups, _), _) <- operationGroupsOperationsAccounts
            } yield (opGroups.hash, opGroups.branch, opGroups.kind, opGroups.block, opGroups.level,
              opGroups.slots, opGroups.signature, opGroups.proposals, opGroups.period, opGroups.source,
              opGroups.proposal, opGroups.ballot, opGroups.chain, opGroups.counter, opGroups.fee, opGroups.blockId)

          case Some(BlocksOperationGroupsOperations(blocksOperationGroupsOperations)) =>
            for {
              ((_, opGroups), _) <- blocksOperationGroupsOperations
            } yield (opGroups.hash, opGroups.branch, opGroups.kind, opGroups.block, opGroups.level,
              opGroups.slots, opGroups.signature, opGroups.proposals, opGroups.period, opGroups.source,
              opGroups.proposal, opGroups.ballot, opGroups.chain, opGroups.counter, opGroups.fee, opGroups.blockId)

          case Some(BlocksOperationGroupsOperationsAccounts(blocksOperationGroupsOperationsAccounts)) =>
            for {
              (((_, opGroups), _), _) <- blocksOperationGroupsOperationsAccounts
            } yield (opGroups.hash, opGroups.branch, opGroups.kind, opGroups.block, opGroups.level,
              opGroups.slots, opGroups.signature, opGroups.proposals, opGroups.period, opGroups.source,
              opGroups.proposal, opGroups.ballot, opGroups.chain, opGroups.counter, opGroups.fee, opGroups.blockId)

          case None =>
            throw new Exception("")
        }

        //sort by hash
        val sortedAction = action.sortBy(_._1.desc)
        val op = dbHandle.run(sortedAction.distinct.take(getFilterLimit(filter)).result)
        val results = Await.result(op, Duration.Inf)
        results.map(x => Tables.OperationGroupsRow(
          x._1, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9, x._10, x._11, x._12, x._13, x._14, x._15, x._16))
      }
    }

  /**
    * Fetches an account by account id from the db.
    * @param account_id The account's id number
    * @return           The account with its associated operation groups
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
    * @param filter Filters to apply
    * @return       List of accounts
    */
  def fetchAccounts(filter: Filter): Try[Seq[AccountsRow]] = {
    getFilteredTables(filter).flatMap { filteredTables => Try {
      val blockFlag = isBlockFilter(filter)
      val operationGroupFlag = isOperationGroupFilter(filter)
      val operationFlag = isOperationFilter(filter)
      val accountFlag = true
      val joinedTables = getJoinedTables(blockFlag, operationGroupFlag, operationFlag, accountFlag, filteredTables)
      val action = joinedTables match {

        case Some(Accounts(accounts)) =>
          for {
            a <- accounts
          } yield (a.accountId, a.blockId, a.manager, a.spendable, a.delegateSetable, a.delegateValue, a.counter, a.script, a.balance)

        case Some(OperationGroupsAccounts(operationGroupsAccounts)) =>
          for {
            (_, a) <- operationGroupsAccounts
          } yield (a.accountId, a.blockId, a.manager, a.spendable, a.delegateSetable, a.delegateValue, a.counter, a.script, a.balance)

        case Some(OperationGroupsOperationsAccounts(operationGroupsOperationsAccounts)) =>
          for {
            ((_, _), a) <- operationGroupsOperationsAccounts
          } yield (a.accountId, a.blockId, a.manager, a.spendable, a.delegateSetable, a.delegateValue, a.counter, a.script, a.balance)

        case _ =>
          throw new Exception("Accounts can only be fetched with no filter, or filters on operation groups or operations.")
      }

      //sort by accountId
      val sortedAction = action.sortBy(_._1.desc)
      val op = dbHandle.run(sortedAction.distinct.take(getFilterLimit(filter)).result)
      val results = Await.result(op, Duration.Inf)
      results.map(x => Tables.AccountsRow(x._1, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9))
      }
    }
  }

}


