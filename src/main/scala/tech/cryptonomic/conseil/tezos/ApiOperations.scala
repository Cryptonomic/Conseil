package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{ColumnOrdered, Rep}
import tech.cryptonomic.conseil
import tech.cryptonomic.conseil.tezos
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.Tables.{AccountsRow, FeesRow}
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

/**
  * Functionality for fetching data from the Conseil database.
  */
object ApiOperations {

  private val conf = ConfigFactory.load
  val awaitTimeInSeconds = conf.getInt("dbAwaitTimeInSeconds")
  lazy val dbHandle: Database = DatabaseUtil.db

  import Filter._

  /**
    * Repesents a query filter submitted to the Conseil API.
    *
    * @param limit                  How many records to return
    * @param blockIDs               Block IDs
    * @param levels                 Block levels
    * @param chainIDs               Chain IDs
    * @param protocols              Protocols
    * @param operationGroupIDs      Operation IDs
    * @param operationSources       Operation sources
    * @param operationDestinations  Operation destinations
    * @param operationParticipants  Operations sources or destinations
    * @param accountIDs             Account IDs
    * @param accountManagers        Account managers
    * @param accountDelegates       Account delegates
    * @param operationKinds         Operation outer kind
    * @param sortBy                 Database column name to sort by
    * @param order                  Sort items ascending or descending
    */
  case class Filter(
                     limit: Option[Int] = Some(defaultLimit),
                     blockIDs: Option[Set[String]] = emptyOptions,
                     levels: Option[Set[Int]] = emptyOptions,
                     chainIDs: Option[Set[String]] = emptyOptions,
                     protocols: Option[Set[String]] = emptyOptions,
                     operationGroupIDs: Option[Set[String]] = emptyOptions,
                     operationSources: Option[Set[String]] = emptyOptions,
                     operationDestinations: Option[Set[String]] = emptyOptions,
                     operationParticipants: Option[Set[String]] = emptyOptions,
                     operationKinds: Option[Set[String]] = emptyOptions,
                     accountIDs: Option[Set[String]] = emptyOptions,
                     accountManagers: Option[Set[String]] = emptyOptions,
                     accountDelegates: Option[Set[String]] = emptyOptions,
                     sortBy: Option[String] = None,
                     order: Option[String] = Some("DESC")
                   )

  object Filter {

    // Common values

    val defaultLimit = 10

    private def emptyOptions[A] = Some(Set.empty[A])
    private[this] def nonEmpty(subFilter: Option[Set[_]]) = subFilter.exists(_.nonEmpty)

    // Predicates to determine existence of specific type of filter

    def isBlockFilter(filter: Filter): Boolean =
      nonEmpty(filter.blockIDs) ||
      nonEmpty(filter.levels) ||
      nonEmpty(filter.chainIDs) ||
      nonEmpty(filter.protocols)

    def isOperationGroupFilter(filter: Filter): Boolean =
      nonEmpty(filter.operationGroupIDs) ||
      nonEmpty(filter.operationSources)

    def isOperationFilter(filter: Filter): Boolean =
      nonEmpty(filter.operationKinds) ||
      nonEmpty(filter.operationSources) ||
      nonEmpty(filter.operationDestinations)

    def isAccountFilter(filter: Filter): Boolean =
      nonEmpty(filter.accountDelegates) ||
      nonEmpty(filter.accountIDs) ||
      nonEmpty(filter.accountManagers)

    // Start helper functions for constructing Slick queries

    def filterBlockIDs(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
      filter.blockIDs.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || b.hash.inSet(set)
      )

    def filterBlockLevels(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
      filter.levels.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || b.level.inSet(set)
      )

    def filterChainIDs(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
      filter.chainIDs.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || b.chainId.getOrElse("").inSet(set)
      )

    def filterProtocols(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
      filter.protocols.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || b.protocol.inSet(set)
      )

    def filterOperationIDs(filter: Filter, og: Tables.OperationGroups): Rep[Boolean] =
      filter.operationGroupIDs.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || og.hash.inSet(set)
      )

    def filterOperationIDs(filter: Filter, o: Tables.Operations): Rep[Boolean] =
      filter.operationGroupIDs.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || o.operationGroupHash.inSet(set)
      )

    def filterOperationSources(filter: Filter, o: Tables.Operations): Rep[Boolean] =
      filter.operationSources.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || o.source.getOrElse("").inSet(set)
      )

    def filterOperationDestinations(filter: Filter, o: Tables.Operations): Rep[Boolean] =
      filter.operationDestinations.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || o.destination.getOrElse("").inSet(set)
      )

    def filterOperationParticipants(filter: Filter, o: Tables.Operations): Rep[Boolean] =
      filter.operationParticipants.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || o.destination.getOrElse(o.source.getOrElse("")).inSet(set)
      )

    def filterAccountIDs(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
      filter.accountIDs.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || a.accountId.inSet(set)
      )

    def filterAccountManagers(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
      filter.accountManagers.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || a.manager.inSet(set)
      )

    def filterAccountDelegates(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
      filter.accountDelegates.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || a.delegateValue.getOrElse("").inSet(set)
      )

    def filterOperationKinds(filter: Filter, o: Tables.Operations): Rep[Boolean] =
      filter.operationKinds.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || o.kind.inSet(set)
      )

    def filterOperationKindsForFees(filter: Filter, fee: Tables.Fees): Rep[Boolean] =
      filter.operationKinds.fold(ifEmpty = true.bind)(
        set => set.isEmpty.bind || fee.kind.inSet(set)
      )

    def getFilterLimit(filter: Filter): Int = filter.limit.getOrElse(Filter.defaultLimit)

  }

  /**
    * Represents queries for filtered tables for Accounts, Blocks, Operation Groups, and Operations.
    *
    * @param filteredAccounts         Filtered Accounts table
    * @param filteredBlocks           Filtered Blocks table
    * @param filteredOperationGroups  Filtered OperationGroups table
    * @param filteredOperations       Filtered Operations table
   */
  case class FilteredTables(
                             filteredAccounts: Query[Tables.Accounts, Tables.AccountsRow, Seq],
                             filteredBlocks: Query[Tables.Blocks, Tables.BlocksRow, Seq],
                             filteredOperationGroups: Query[Tables.OperationGroups, Tables.OperationGroupsRow, Seq],
                             filteredOperations: Query[Tables.Operations, Tables.OperationsRow, Seq]
                           )

  /**
    * Represents all possible joins of tables that can be made from Accounts, Blocks, Operation Groups, and Operations.
    *
    * Example: BlocksOperationGroupsOperationsAccounts corresponds to the four way inner join between the Blocks,
    * Operation Groups, Operations, and Accounts tables.
    *
    * Example: OperationGroupsOperationsAccounts corresponds to the three way join between the Operation Groups,
    * Operations, and Accounts Tables.
    */

  sealed trait JoinedTables

  case class BlocksOperationGroupsOperationsAccounts(
    join: Query[
      (Tables.Blocks, Tables.OperationGroups, Tables.Operations, Tables.Accounts),
      (Tables.BlocksRow, Tables.OperationGroupsRow, Tables.OperationsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksOperationGroupsOperations(
    join: Query[
      (Tables.Blocks, Tables.OperationGroups, Tables.Operations),
      (Tables.BlocksRow, Tables.OperationGroupsRow, Tables.OperationsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksOperationGroupsAccounts(
    join: Query[
      (Tables.Blocks, Tables.OperationGroups, Tables.Accounts),
      (Tables.BlocksRow, Tables.OperationGroupsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksOperationGroups(
    join: Query[
      (Tables.Blocks, Tables.OperationGroups),
      (Tables.BlocksRow, Tables.OperationGroupsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksOperationsAccounts(
    join: Query[
      (Tables.Blocks, Tables.Operations, Tables.Accounts),
      (Tables.BlocksRow, Tables.OperationsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksOperations(
    join: Query[
      (Tables.Blocks, Tables.Operations),
      (Tables.BlocksRow, Tables.OperationsRow),
      Seq]
  ) extends JoinedTables

  case class BlocksAccounts(
    join: Query[
      (Tables.Blocks, Tables.Accounts),
      (Tables.BlocksRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class Blocks(
    join: Query[Tables.Blocks, Tables.BlocksRow, Seq]
  ) extends JoinedTables

  case class OperationGroupsOperationsAccounts(
    join: Query[
      (Tables.OperationGroups, Tables.Operations, Tables.Accounts),
      (Tables.OperationGroupsRow, Tables.OperationsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class OperationGroupsOperations(
    join: Query[
      (Tables.OperationGroups, Tables.Operations),
      (Tables.OperationGroupsRow, Tables.OperationsRow),
      Seq]
  ) extends JoinedTables

  case class OperationGroupsAccounts(
    join: Query[
      (Tables.OperationGroups, Tables.Accounts),
      (Tables.OperationGroupsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class OperationGroups(
    join: Query[Tables.OperationGroups, Tables.OperationGroupsRow, Seq]
  ) extends JoinedTables

  case class OperationsAccounts(
    join: Query[
      (Tables.Operations, Tables.Accounts),
      (Tables.OperationsRow, Tables.AccountsRow),
      Seq]
  ) extends JoinedTables

  case class Operations(
    join: Query[Tables.Operations, Tables.OperationsRow, Seq]
  ) extends JoinedTables

  case class Accounts(
    join: Query[Tables.Accounts, Tables.AccountsRow, Seq]
  ) extends JoinedTables

  case object EmptyJoin extends JoinedTables

  /**
    * This represents a database query that returns all of the columns of the table in a scala tuple.
    * The only options available are for the Blocks, Operation Groups, and Operations Table,
    * corresponding to the functions fetchBlocks, fetchOperationGroups, and fetchOperations, and these
    * types are used for convenience in fetchSortedTables.
    */
  sealed trait Action

  case class BlocksAction(action: Query[Tables.Blocks, Tables.BlocksRow, Seq]) extends Action

  case class OperationGroupsAction
    (action: Query[Tables.OperationGroups, Tables.OperationGroupsRow, Seq]) extends Action


  case class AccountsAction(action: Query[Tables.Accounts, Tables.AccountsRow, Seq]) extends Action

  /**
    * Filters Accounts, Operation Groups, Operations, and Blocks tables based on users input to the filter.
    * @param filter Filter parameters.
    * @return
    */
  private def getFilteredTables(filter: Filter): Try[FilteredTables] = {

    def filterOn(maxLevel: BigDecimal): FilteredTables = {
      val filteredAccounts = Tables.Accounts.filter(account =>
        filterAccountIDs(filter, account) &&
          filterAccountDelegates(filter, account) &&
          filterAccountManagers(filter, account) &&
          account.blockLevel === maxLevel)

      val filteredOpGroups = Tables.OperationGroups.filter({ opGroup =>
        filterOperationIDs(filter, opGroup)
      })

      val filteredOps = Tables.Operations.filter({ op =>
        filterOperationKinds(filter, op) &&
          filterOperationDestinations(filter, op) &&
          filterOperationSources(filter, op)
      })

      val filteredBlocks = Tables.Blocks.filter({ block =>
        filterBlockIDs(filter, block) &&
          filterBlockLevels(filter, block) &&
          filterChainIDs(filter, block) &&
          filterProtocols(filter, block)
      })

      FilteredTables(filteredAccounts, filteredBlocks, filteredOpGroups, filteredOps)
    }

    for {
      _ <- fetchLatestBlock() // is this needed?
      maxLevelForAccounts <- fetchMaxBlockLevelForAccounts()
    } yield filterOn(maxLevelForAccounts)
  }

  /**
    * Returns the join of some combination of the Blocks, Operation Groups, Operations, and Accounts
    * tables, given the flags of which tables are necessary. If a table shouldn't be created based
    * on domain specific knowledge of Tezos, return None.
    * @param blockFlag          Flag which affirms that blocks need to be included in the join.
    * @param operationGroupFlag Flag which affirms that operationGroups need to be included in the join.
    * @param operationFlag      Flag which affirms that operations need to be included in the join.
    * @param accountFlag        Flag which affirms that accounts need to be included in the join.
    * @param tables             Product which contains queries for all tables.
    * @param filter             Used for filtering when
    * @return
    */
  private def getJoinedTables(blockFlag: Boolean,
                              operationGroupFlag: Boolean,
                              operationFlag: Boolean,
                              accountFlag: Boolean,
                              tables: FilteredTables,
                              filter: Filter): Option[JoinedTables] = {

    val FilteredTables(accounts, blocks, operationGroups, operations)= tables

    def unwrapQuadJoin[A,B,C,D](nest: (((A,B),C),D)): (A,B,C,D) = nest match {
      case (((a,b),c),d) => (a,b,c,d)
    }

    def unwrapTripleJoin[A,B,C](nest: ((A,B),C)): (A,B,C) = nest match {
      case ((a,b),c) => (a,b,c)
    }

    (blockFlag, operationGroupFlag, operationFlag, accountFlag) match {
      case (true, true, true, true) =>
        Some(BlocksOperationGroupsOperationsAccounts(
          blocks
            .join(operationGroups).on(_.hash === _.blockId)
            .join(operations).on(_._2.hash === _.operationGroupHash)
            .join(accounts).on(_._2.source === _.accountId)
            .map(unwrapQuadJoin)
        ))
      case (true, true, true, false) =>
        Some(BlocksOperationGroupsOperations(
          blocks
            .join(operationGroups).on(_.hash === _.blockId)
            .join(operations).on(_._2.hash === _.operationGroupHash)
            .map(unwrapTripleJoin)
        ))
      case (true, true, false, true) =>
        None
      case (true, true, false, false) =>
        Some(BlocksOperationGroups(
          blocks.join(operationGroups).on(_.hash === _.blockId)))
      case (true, false, true, true)
           | (true, false, true, false)
           | (true, false, false, true) => None
      case (true, false, false, false) =>
        Some(Blocks(blocks))
      case (false, true, true, true) =>
        Some(OperationGroupsOperationsAccounts(
          operationGroups
            .join(operations).on(_.hash === _.operationGroupHash)
            .join(accounts).on(_._2.source === _.accountId)
            .map(unwrapTripleJoin)
        ))
      case (false, true, true, false) =>
        Some(OperationGroupsOperations(
          operationGroups
            .join(operations).on(_.hash === _.operationGroupHash)))
      case (false, true, false, true) =>
          Some(OperationGroupsOperationsAccounts(
            operationGroups
              .join(operations).on(_.hash === _.operationGroupHash)
              .join(accounts).on(_._2.source === _.accountId)
              .map(unwrapTripleJoin)
          ))
      case (false, true, false, false) =>
        Some(OperationGroups(operationGroups))
      case (false, false, true, true) =>
        Some(OperationGroupsOperationsAccounts(operationGroups
          .join(operations).on(_.hash === _.operationGroupHash)
          .join(accounts).on(_._2.source === _.accountId)
          .map(unwrapTripleJoin)
        ))
      case (false, false, true, false) =>
        None
      case (false, false, false, true) =>
        Some(Accounts(accounts))
      case (false, false, false, false) =>
        None
    }
  }

  /**
    * Return table query which is the sorted verion of action, based on database column name, sortBy, and the order.
    * This will be refactored out later, as this is just an initial solution to the user wanting to sort by columns
    * according to the current schema. This will break if the schema changes.
    * @param order  Parameter to determine whether to sort in ascending or descending order.
    * @param action The query for the table we want to sort.
    * @param sortBy Parameter to say what column to sort by.
    * @return
    */
  private def fetchSortedAction(order: Option[String], action: Action, sortBy: Option[String]): Action = {

    val sortingField: Option[String] = sortBy.map(_.toLowerCase)

    def sortingOrder[A](col: ColumnOrdered[A]): ColumnOrdered[A] =
      order.map(_.toLowerCase) match {
        case Some("asc") => col.asc
        case _ => col.desc
      }

    //default is sorting descending by block level, operation group hash, and account ID, otherwise order and sorting column chosen by user
    val sortedAction =
      action match {
        case blocks: BlocksAction =>
          val columnToSort = sortingField.map {
            case "level" => t: Tables.Blocks => sortingOrder(t.level)
            case "proto" => t: Tables.Blocks => sortingOrder(t.proto)
            case "predecessor" => t: Tables.Blocks => sortingOrder(t.predecessor)
            case "timestamp" => t: Tables.Blocks => sortingOrder(t.timestamp)
            case "validation_pass" => t: Tables.Blocks => sortingOrder(t.validationPass)
            case "fitness" => t: Tables.Blocks => sortingOrder(t.fitness)
            case "context" => t: Tables.Blocks => sortingOrder(t.context)
            case "signature" => t: Tables.Blocks => sortingOrder(t.signature)
            case "protocol" => t: Tables.Blocks => sortingOrder(t.protocol)
            case "chain_id" => t: Tables.Blocks => sortingOrder(t.chainId)
            case "hash" => t: Tables.Blocks => sortingOrder(t.hash)
            case "operations_hash" => t: Tables.Blocks => sortingOrder(t.operationsHash)
          } getOrElse {
            t: Tables.Blocks => sortingOrder(t.level)
          }
          blocks.copy(action = blocks.action.sortBy(columnToSort))

        case groups: OperationGroupsAction =>
          val columnToSort = sortingField.map {
            case "protocol" => t: Tables.OperationGroups => sortingOrder(t.protocol)
            case "chain_id" => t: Tables.OperationGroups => sortingOrder(t.chainId)
            case "hash" => t: Tables.OperationGroups => sortingOrder(t.hash)
            case "branch" => t: Tables.OperationGroups => sortingOrder(t.branch)
            case "signature" => t: Tables.OperationGroups => sortingOrder(t.signature)
            case "block_id" => t: Tables.OperationGroups => sortingOrder(t.blockId)
          } getOrElse {
            t: Tables.OperationGroups => sortingOrder(t.hash)
          }
          groups.copy(action = groups.action.sortBy(columnToSort))

        case accounts: AccountsAction =>
          val columnToSort = sortingField.map {
            case "account_id" => t: Tables.Accounts => sortingOrder(t.accountId)
            case "block_id" => t: Tables.Accounts => sortingOrder(t.blockId)
            case "manager" => t: Tables.Accounts => sortingOrder(t.manager)
            case "spendable" => t: Tables.Accounts => sortingOrder(t.spendable)
            case "delegate_setable" => t: Tables.Accounts => sortingOrder(t.delegateSetable)
            case "delegate_value" => t: Tables.Accounts => sortingOrder(t.delegateValue)
            case "counter" => t: Tables.Accounts => sortingOrder(t.counter)
            case "script" => t: Tables.Accounts => sortingOrder(t.script)
            case "balance" => t: Tables.Accounts => sortingOrder(t.balance)
          } getOrElse {
            t: Tables.Accounts => sortingOrder(t.accountId)
          }
          accounts.copy(action = accounts.action.sortBy(columnToSort))

      }

    sortedAction
  }

  /**
    * Fetches the level of the most recent block stored in the database.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxLevel(): Try[Int] = Try {
    val op: Future[Option[Int]] = dbHandle.run(Tables.Blocks.map(_.level).max.result)
    val maxLevelOpt = Await.result(op, awaitTimeInSeconds.seconds)
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
        Await.result(op, awaitTimeInSeconds.seconds).head
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
    val block = Await.result(op, awaitTimeInSeconds.seconds).head
    val op2 = dbHandle.run(Tables.OperationGroups.filter(_.blockId === hash).result)
    val operationGroups = Await.result(op2, awaitTimeInSeconds.seconds)
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

        // Blocks need to be fetched, other tables needed if user asks for them via the filter
        val blockFlag = true
        val operationGroupFlag = isOperationGroupFilter(filter)
        val operationFlag = isOperationFilter(filter)
        val accountFlag = isAccountFilter(filter)
        val joinedTables = getJoinedTables(blockFlag, operationGroupFlag, operationFlag, accountFlag, filteredTables, filter)

        val action = joinedTables match {

          case Some(Blocks(blocks)) => blocks

          case Some(BlocksOperationGroups(blocksOperationGroups)) =>
            blocksOperationGroups.map { case (b, _) => b }

          case Some(BlocksOperationGroupsOperations(blocksOperationGroupsOperations)) =>
            blocksOperationGroupsOperations.map { case (b, _, _) => b }

          case _ =>
            throw new IllegalArgumentException("You can only filter blocks by block ID, level, chain ID, protocol, operation ID, operation source, or inner and outer operation kind.")

        }

        val BlocksAction(sortedAction) = fetchSortedAction(filter.order, BlocksAction(action), filter.sortBy)
        val op = dbHandle.run(sortedAction.distinct.take(getFilterLimit(filter)).result)
        Await.result(op, awaitTimeInSeconds.seconds)
      }
    }

  /**
    * Fetch a given operation group
    * @param operationGroupHash Operation group hash
    * @return Operation group along with associated operations and accounts
    */
  def fetchOperationGroup(operationGroupHash: String): Try[Map[String, Any]] =
    fetchLatestBlock().flatMap { latestBlock =>
      Try {
        val op = dbHandle.run(Tables.OperationGroups.filter(_.hash === operationGroupHash).take(1).result)
        val opGroup = Await.result(op, awaitTimeInSeconds.seconds).head
        val op2 = dbHandle.run(Tables.Operations.filter(_.operationGroupHash === operationGroupHash).result)
        val operations = Await.result(op2, awaitTimeInSeconds.seconds)
        Map(
          "operation_group" -> opGroup,
          "operations" -> operations
        )
      }
    }

  /**
    * Fetches all operation groups.
    * @param filter Filters to apply
    * @return List of operation groups
    */
  def fetchOperationGroups(filter: Filter)(implicit apiFilters: ApiFiltering[Try, Tables.OperationGroupsRow]): Try[Seq[Tables.OperationGroupsRow]] =
    fetchMaxBlockLevelForAccounts().flatMap(apiFilters(filter))

  /**
    * Fetches all operations.
    * @param filter Filters to apply
    * @return List of operations
    */
  def fetchOperations(filter: Filter): Try[Seq[Tables.OperationsRow]] = Try{
    val action = for {
      o <- Tables.Operations
      og <- o.operationGroupsFk
      b <- o.blocksFk
    } yield (o, b)

    val filtered = action.filter { case (o, b) =>
      filterOperationIDs(filter, o) &&
      filterOperationSources(filter, o) &&
      filterOperationDestinations(filter, o) &&
      filterOperationParticipants(filter, o) &&
      filterOperationKinds(filter, o) &&
      filterBlockIDs(filter, b) &&
      filterBlockLevels(filter, b) &&
      filterChainIDs(filter, b) &&
      filterProtocols(filter, b)
    }.map(_._1)

    val op = dbHandle.run(
      filtered.distinct
        .sortBy(_.blockLevel.desc)
        .take(ApiFiltering.getFilterLimit(filter))
        .result
    )
    Await.result(op, awaitTimeInSeconds.seconds)
  }

  /**
    * Given the operation kind and the number of columns wanted,
    *
    * return the mean (along with +/- one standard deviation) of
    * fees incurred in those operations.
    * @param filter Filters to apply, specifically operation kinds
    * @return AverageFee class, getting low, medium, and high
    *         estimates for average fees, timestamp the calculation
    *         was performed at, and the kind of operation being
    *         averaged over.
    */
  def fetchAverageFees(filter: Filter): Try[AverageFees] = Try {
    val action =
      Tables.Fees
        .filter (
          fee => filterOperationKindsForFees(filter, fee)
        )
        .distinct
        .sortBy(_.timestamp.desc)
        .take(1)
        .result
        .head

    val row = Await.result(dbHandle.run(action), awaitTimeInSeconds.seconds)
    row match {
      case FeesRow(low, medium, high, timestamp, kind) =>
       AverageFees(low, medium, high, timestamp, kind)
    }

  }

  /**
    * Fetches the level of the most recent block in the accounts table.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxBlockLevelForAccounts(): Try[BigDecimal] = Try {
    val op = dbHandle.run(Tables.Accounts.map(_.blockLevel).max.result)
    val maxLevelOpt = Await.result(op, awaitTimeInSeconds.seconds)
    maxLevelOpt.getOrElse(-1)
  }

  /**
    * Fetches an account by account id from the db.
    * @param account_id The account's id number
    * @return           The account with its associated operation groups
    */
  def fetchAccount(account_id: String): Try[Map[String, Any]] =
    fetchMaxBlockLevelForAccounts().flatMap { latestBlockLevel =>
      Try {
        val op = dbHandle.run(Tables.Accounts
          .filter(_.blockLevel === latestBlockLevel)
          .filter(_.accountId === account_id).take(1).result)
        val account = Await.result(op, awaitTimeInSeconds.seconds).head
        Map("account" -> account)
      }
    }

  /**
    * Fetches a list of accounts from the db.
    * @param filter Filters to apply
    * @return       List of accounts
    */
  def fetchAccounts(filter: Filter)(implicit apiFilters: ApiFiltering[Try, Tables.AccountsRow]): Try[Seq[Tables.AccountsRow]] =
    fetchMaxBlockLevelForAccounts().flatMap(apiFilters(filter))

}


