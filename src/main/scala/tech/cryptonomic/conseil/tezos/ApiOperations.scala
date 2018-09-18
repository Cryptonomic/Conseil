package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp

import com.typesafe.config.ConfigFactory
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.Tables.BlocksRow
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{Duration, _}
import scala.util.Try


/**
  * Functionality for fetching data from the Conseil database.
  */
object ApiOperations {

  private val conf = ConfigFactory.load
  private val awaitTimeInSeconds = conf.getInt("dbAwaitTimeInSeconds")
  lazy val dbHandle: Database = DatabaseUtil.db

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
                     limit: Option[Int] = Some(10),
                     blockIDs: Option[Set[String]] = Some(Set[String]()),
                     levels: Option[Set[Int]] = Some(Set[Int]()),
                     chainIDs: Option[Set[String]] = Some(Set[String]()),
                     protocols: Option[Set[String]] = Some(Set[String]()),
                     operationGroupIDs: Option[Set[String]] = Some(Set[String]()),
                     operationSources: Option[Set[String]] = Some(Set[String]()),
                     operationDestinations: Option[Set[String]] = Some(Set[String]()),
                     operationParticipants: Option[Set[String]] = Some(Set[String]()),
                     operationKinds: Option[Set[String]] = Some(Set[String]()),
                     accountIDs: Option[Set[String]] = Some(Set[String]()),
                     accountManagers: Option[Set[String]] = Some(Set[String]()),
                     accountDelegates: Option[Set[String]] = Some(Set[String]()),
                     sortBy: Option[String] = None,
                     order: Option[String] = Some("DESC")
                   )


  /**
    * Represents queries for filtered tables for Accounts, Blocks, Operation Groups, and Operations.
    *
    * @param filteredAccounts         Filtered Accounts table
    * @param filteredBlocks           Filtered Blocks table
    * @param filteredOperationGroups  Filtered OperationGroups table
    * @param filteredOperations       Filtered Operations table
   */
  case class FilteredTables(
                             filteredAccounts: Query[Tables.Accounts, Tables.Accounts#TableElementType, Seq],
                             filteredBlocks: Query[Tables.Blocks, Tables.Blocks#TableElementType, Seq],
                             filteredOperationGroups: Query[Tables.OperationGroups, Tables.OperationGroups#TableElementType, Seq],
                             filteredOperations: Query[Tables.Operations, Tables.Operations#TableElementType, Seq]
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

  /**
    * This represents a database query that returns all of the columns of the table in a scala tuple.
    * The only options available are for the Blocks, Operation Groups, and Operations Table,
    * corresponding to the functions fetchBlocks, fetchOperationGroups, and fetchOperations, and these
    * types are used for convenience in fetchSortedTables.
    */
  sealed trait Action

  case class BlocksAction
    (action: Query[(Rep[Int], Rep[Int], Rep[String], Rep[Timestamp], Rep[Int],
                    Rep[String], Rep[Option[String]], Rep[Option[String]], Rep[String],
                    Rep[Option[String]], Rep[String], Rep[Option[String]]),
                   (Int, Int, String, Timestamp, Int, String,
                     Option[String], Option[String],
                     String, Option[String], String, Option[String]),
                   Seq]) extends Action

  case class OperationGroupsAction
    (action: Query[(Rep[String], Rep[Option[String]], Rep[String], Rep[String],
                    Rep[Option[String]], Rep[String]),
                   (String, Option[String], String, String, Option[String], String),
                   Seq]) extends Action


  case class AccountsAction
  (action: Query [(Rep[String], Rep[String], Rep[String], Rep[Boolean], Rep[Boolean], Rep[Option[String]], Rep[Int], Rep[Option[String]], Rep[BigDecimal], Rep[BigDecimal]),
                  (String, String, String, Boolean, Boolean, Option[String], Int, Option[String], BigDecimal, BigDecimal),
                  Seq]) extends Action

  // Predicates to determine existence of specific type of filter

  private def isBlockFilter(filter: Filter): Boolean =
    (filter.blockIDs.isDefined && filter.blockIDs.get.nonEmpty) ||
      (filter.levels.isDefined && filter.levels.get.nonEmpty) ||
      (filter.chainIDs.isDefined && filter.chainIDs.get.nonEmpty) ||
      (filter.protocols.isDefined && filter.protocols.get.nonEmpty)

  private def isOperationGroupFilter(filter: Filter): Boolean =
    (filter.operationGroupIDs.isDefined && filter.operationGroupIDs.get.nonEmpty) ||
      (filter.operationSources.isDefined && filter.operationSources.get.nonEmpty)

  private def isOperationFilter(filter: Filter): Boolean =
    (filter.operationKinds.isDefined && filter.operationKinds.get.nonEmpty) ||
      (filter.operationSources.isDefined && filter.operationSources.get.nonEmpty) ||
        (filter.operationDestinations.isDefined && filter.operationDestinations.get.nonEmpty)

  private def isAccountFilter(filter: Filter): Boolean =
    (filter.accountDelegates.isDefined && filter.accountDelegates.get.nonEmpty) ||
      (filter.accountIDs.isDefined && filter.accountIDs.get.nonEmpty) ||
      (filter.accountManagers.isDefined && filter.accountManagers.get.nonEmpty)

  // Start helper functions for constructing Slick queries

  private def filterBlockIDs(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.blockIDs.isDefined && filter.blockIDs.get.nonEmpty) b.hash.inSet(filter.blockIDs.get) else true

  private def filterBlockLevels(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.levels.isDefined && filter.levels.get.nonEmpty) b.level.inSet(filter.levels.get) else true

  private def filterChainIDs(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.chainIDs.isDefined && filter.chainIDs.get.nonEmpty)
      b.chainId.getOrElse("").inSet(filter.chainIDs.get) else true

  private def filterProtocols(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
    if (filter.protocols.isDefined && filter.protocols.get.nonEmpty) b.protocol.inSet(filter.protocols.get) else true

  private def filterOperationIDs(filter: Filter, og: Tables.OperationGroups): Rep[Boolean] =
    if (filter.operationGroupIDs.isDefined && filter.operationGroupIDs.get.nonEmpty)
      og.hash.inSet(filter.operationGroupIDs.get)
    else true

  private def filterOperationIDs(filter: Filter, o: Tables.Operations): Rep[Boolean] =
    if (filter.operationGroupIDs.isDefined && filter.operationGroupIDs.get.nonEmpty)
      o.operationGroupHash.inSet(filter.operationGroupIDs.get)
    else true

  private def filterOperationSources(filter: Filter, o: Tables.Operations): Rep[Boolean] =
    if (filter.operationSources.isDefined && filter.operationSources.get.nonEmpty)
      o.source.getOrElse("").inSet(filter.operationSources.get)
    else true

  private def filterOperationDestinations(filter: Filter, o: Tables.Operations): Rep[Boolean] =
    if (filter.operationDestinations.isDefined && filter.operationDestinations.get.nonEmpty)
      o.destination.getOrElse("").inSet(filter.operationDestinations.get)
    else true

  private def filterOperationParticipants(filter: Filter, o: Tables.Operations): Rep[Boolean] =
    if (filter.operationParticipants.isDefined && filter.operationParticipants.get.nonEmpty) {
      o.destination.getOrElse("").inSet(filter.operationParticipants.get) ||
        o.source.getOrElse("").inSet(filter.operationParticipants.get)
    }
    else true

  private def filterAccountIDs(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
    if (filter.accountIDs.isDefined && filter.accountIDs.get.nonEmpty) a.accountId.inSet(filter.accountIDs.get) else true

  private def filterAccountManagers(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
    if (filter.accountManagers.isDefined && filter.accountManagers.get.nonEmpty) a.manager.inSet(filter.accountManagers.get) else true

  private def filterAccountDelegates(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
    if (filter.accountDelegates.isDefined && filter.accountDelegates.get.nonEmpty)
      a.delegateValue.getOrElse("").inSet(filter.accountDelegates.get)
    else true

  private def filterOperationKinds(filter: Filter, o: Tables.Operations): Rep[Boolean] =
    if (filter.operationKinds.isDefined && filter.operationKinds.get.nonEmpty)
      o.kind.inSet(filter.operationKinds.get) else true

  private def filterOperationKindsForFees(filter: Filter, fee: Tables.Fees): Rep[Boolean] =
    if (filter.operationKinds.isDefined && filter.operationKinds.get.nonEmpty)
      fee.kind.inSet(filter.operationKinds.get) else true

  private def getFilterLimit(filter: Filter): Int = if (filter.limit.isDefined) filter.limit.get else 10

  // End helper functions for constructing Slick queries


  /**
    * Filters Accounts, Operation Groups, Operations, and Blocks tables based on users input to the filter.
    * @param filter Filter parameters.
    * @return
    */
  private def filteredTablesIO(filter: Filter)(implicit ec: ExecutionContext): DBIO[FilteredTables] =
    latestBlockIO().collect { // we fail the operation if no block is there
      case Some(_) =>
        TezosDatabaseOperations.accountsMaxBlockLevel.map {
          maxLevelForAccounts =>

            val filteredAccounts = Tables.Accounts.filter(account =>
              filterAccountIDs(filter, account) &&
                filterAccountDelegates(filter, account) &&
                filterAccountManagers(filter, account) &&
                account.blockLevel === maxLevelForAccounts)

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
    }.flatten

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

    val blocks = tables.filteredBlocks
    val operationGroups = tables.filteredOperationGroups
    val operations = tables.filteredOperations
    val accounts = tables.filteredAccounts

    (blockFlag, operationGroupFlag, operationFlag, accountFlag) match {
      case (true, true, true, true) =>
        Some(BlocksOperationGroupsOperationsAccounts(
          blocks
            .join(operationGroups).on(_.hash === _.blockId)
            .join(operations).on(_._2.hash === _.operationGroupHash)
            .join(accounts).on(_._2.source === _.accountId)))
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
            .join(accounts).on(_._2.source === _.accountId)))
      case (false, true, true, false) =>
        Some(OperationGroupsOperations(
          operationGroups
            .join(operations).on(_.hash === _.operationGroupHash)))
      case (false, true, false, true) =>
          Some(OperationGroupsOperationsAccounts(
            operationGroups
              .join(operations).on(_.hash === _.operationGroupHash)
              .join(accounts).on(_._2.source === _.accountId)))
      case (false, true, false, false) =>
        Some(OperationGroups(operationGroups))
      case (false, false, true, true) =>
        Some(OperationGroupsOperationsAccounts(operationGroups
          .join(operations).on(_.hash === _.operationGroupHash)
          .join(accounts).on(_._2.source === _.accountId)))
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

    //default is sorting descending by block level, operation group hash, and account ID, otherwise order and sorting column chosen by user
    val sortedAction =
      action match {
        case BlocksAction(blockAction) =>
          sortBy.map(_.toLowerCase) match {
            case Some(x) if x == "level" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._1.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._1.desc))
                case None => BlocksAction(blockAction.sortBy(_._1.desc))
              }
            case Some(x) if x == "proto" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._2.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._2.desc))
                case None => BlocksAction(blockAction.sortBy(_._2.desc))
              }
            case Some(x) if x == "predecessor" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._3.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._3.desc))
                case None => BlocksAction(blockAction.sortBy(_._3.desc))
              }
            case Some(x) if x == "timestamp" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._4.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._4.desc))
                case None => BlocksAction(blockAction.sortBy(_._4.desc))
              }
            case Some(x) if x == "validation_pass" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._5.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._5.desc))
                case None => BlocksAction(blockAction.sortBy(_._5.desc))
              }

            case Some(x) if x == "fitness" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._6.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._6.desc))
                case None => BlocksAction(blockAction.sortBy(_._6.desc))
              }
            case Some(x) if x == "context" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._7.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._7.desc))
                case None => BlocksAction(blockAction.sortBy(_._7.desc))
              }
            case Some(x) if x == "signature" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._8.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._8.desc))
                case None => BlocksAction(blockAction.sortBy(_._8.desc))
              }
            case Some(x) if x == "protocol" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._9.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._9.desc))
                case None => BlocksAction(blockAction.sortBy(_._9.desc))
              }
            case Some(x) if x == "chain_id" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._10.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._10.desc))
                case None => BlocksAction(blockAction.sortBy(_._10.desc))
              }
            case Some(x) if x == "hash" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._11.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._11.desc))
                case None => BlocksAction(blockAction.sortBy(_._11.desc))
              }
            case Some(x) if x == "operations_hash" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._12.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._12.desc))
                case None => BlocksAction(blockAction.sortBy(_._12.desc))
              }
            case None =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => BlocksAction(blockAction.sortBy(_._1.asc))
                case Some(x) if x == "desc" => BlocksAction(blockAction.sortBy(_._1.desc))
                case None => BlocksAction(blockAction.sortBy(_._1.desc))
              }
          }
        case OperationGroupsAction(opGroupAction) =>
          sortBy.map(_.toLowerCase) match {
            case Some(x) if x == "protocol" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._1.asc))
                case Some(x) if x == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._1.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._1.desc))
              }
            case Some(x) if x == "chain_id" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._2.asc))
                case Some(x) if x == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._2.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._2.desc))
              }
            case Some(x) if x == "hash" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._3.asc))
                case Some(x) if x == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._3.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._3.desc))
              }
            case Some(x) if x == "branch" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._4.asc))
                case Some(x) if x == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._4.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._4.desc))
              }
            case Some(x) if x == "signature" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._5.asc))
                case Some(x) if x == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._5.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._5.desc))
              }
            case Some(x) if x == "block_id" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._6.asc))
                case Some(x) if x == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._6.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._6.desc))
              }
            case None =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._3.asc))
                case Some(x) if x == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._3.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._3.desc))
              }
          }
        case AccountsAction(accountAction) =>
          sortBy.map(_.toLowerCase) match {
            case Some(x) if x == "account_id" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => AccountsAction(accountAction.sortBy(_._1.asc))
                case Some(x) if x == "desc" => AccountsAction(accountAction.sortBy(_._1.desc))
                case None => AccountsAction(accountAction.sortBy(_._1.desc))
              }
            case Some(x) if x == "block_id" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => AccountsAction(accountAction.sortBy(_._2.asc))
                case Some(x) if x == "desc" => AccountsAction(accountAction.sortBy(_._2.desc))
                case None => AccountsAction(accountAction.sortBy(_._2.desc))
              }
            case Some(x) if x == "manager" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => AccountsAction(accountAction.sortBy(_._3.asc))
                case Some(x) if x == "desc" => AccountsAction(accountAction.sortBy(_._3.desc))
                case None => AccountsAction(accountAction.sortBy(_._3.desc))
              }
            case Some(x) if x == "spendable" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => AccountsAction(accountAction.sortBy(_._4.asc))
                case Some(x) if x == "desc" => AccountsAction(accountAction.sortBy(_._4.desc))
                case None => AccountsAction(accountAction.sortBy(_._4.desc))
              }
            case Some(x) if x == "delegate_setable" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => AccountsAction(accountAction.sortBy(_._5.asc))
                case Some(x) if x == "desc" => AccountsAction(accountAction.sortBy(_._5.desc))
                case None => AccountsAction(accountAction.sortBy(_._5.desc))
              }
            case Some(x) if x == "delegate_value" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => AccountsAction(accountAction.sortBy(_._6.asc))
                case Some(x) if x == "desc" => AccountsAction(accountAction.sortBy(_._6.desc))
                case None => AccountsAction(accountAction.sortBy(_._6.desc))
              }
            case Some(x) if x == "counter" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => AccountsAction(accountAction.sortBy(_._7.asc))
                case Some(x) if x == "desc" => AccountsAction(accountAction.sortBy(_._7.desc))
                case None => AccountsAction(accountAction.sortBy(_._7.desc))
              }
            case Some(x) if x == "script" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => AccountsAction(accountAction.sortBy(_._8.asc))
                case Some(x) if x == "desc" => AccountsAction(accountAction.sortBy(_._8.desc))
                case None => AccountsAction(accountAction.sortBy(_._8.desc))
              }
            case Some(x) if x == "balance" =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => AccountsAction(accountAction.sortBy(_._9.asc))
                case Some(x) if x == "desc" => AccountsAction(accountAction.sortBy(_._9.desc))
                case None => AccountsAction(accountAction.sortBy(_._9.desc))
              }
            case None =>
              order.map(_.toLowerCase) match {
                case Some(x) if x == "asc" => AccountsAction(accountAction.sortBy(_._1.asc))
                case Some(x) if x == "desc" => AccountsAction(accountAction.sortBy(_._1.desc))
                case None => AccountsAction(accountAction.sortBy(_._1.desc))
              }
          }
      }

    sortedAction
  }

  /**
    * Fetches the level of the most recent block stored in the database.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxLevel(implicit ec: ExecutionContext): Future[Int] = {
    val optionalMax: Future[Option[Int]] = dbHandle.run(Tables.Blocks.map(_.level).max.result)
    optionalMax.map(_.getOrElse(-1))
  }

  /**
    * Fetches the most recent block stored in the database.
    *
    * @return Latest block.
    */
  def fetchLatestBlock()(implicit ec: ExecutionContext): Future[Option[Tables.BlocksRow]] =
    dbHandle.run(latestBlockIO())

  /**
    * Fetches a block by block hash from the db.
    *
    * @param hash The block's hash
    * @return The block along with its operations
    */
  def fetchBlock(hash: String): Try[Map[String, Any]] = Try {
    val op = dbHandle.run(Tables.Blocks.filter(_.hash === hash).take(1).result)
    val block = Await.result(op, Duration.apply(awaitTimeInSeconds, SECONDS)).head
    val op2 = dbHandle.run(Tables.OperationGroups.filter(_.blockId === hash).result)
    val operationGroups = Await.result(op2, Duration.apply(awaitTimeInSeconds, SECONDS))
    Map("block" -> block, "operation_groups" -> operationGroups)
  }


  private def extractFromBlock(b: Tables.Blocks) =
    (b.level,
      b.proto,
      b.predecessor,
      b.timestamp,
      b.validationPass,
      b.fitness,
      b.context,
      b.signature,
      b.protocol,
      b.chainId,
      b.hash,
      b.operationsHash
    )

  /**
    * Fetches all blocks from the db.
    *
    * @param filter Filters to apply
    * @return List of blocks
    */
  def fetchBlocks(filter: Filter)(implicit ec: ExecutionContext): Future[Seq[Tables.BlocksRow]] = {
    val filteringIO = filteredTablesIO(filter).flatMap {
      filtered =>

        // Blocks need to be fetched, other tables needed if user asks for them via the filter
        val blockFlag = true
        val operationGroupFlag = isOperationGroupFilter(filter)
        val operationFlag = isOperationFilter(filter)
        val accountFlag = isAccountFilter(filter)
        val joinedTables = getJoinedTables(blockFlag, operationGroupFlag, operationFlag, accountFlag, filtered, filter)

        //there will be some action only if the joined tables have the expected shape
        val validAction = joinedTables.collect {

          case Blocks(blocks) =>
            for {
              b <- blocks
            } yield extractFromBlock(b)

          case BlocksOperationGroups(blocksOperationGroups) =>
            for {
              (b, _) <- blocksOperationGroups
            } yield extractFromBlock(b)

          case BlocksOperationGroupsOperations(blocksOperationGroupsOperations) =>
            for {
              ((b, _), _) <- blocksOperationGroupsOperations
            } yield extractFromBlock(b)
        }

        validAction.map {
          action =>
            val BlocksAction(sortedAction) = fetchSortedAction(filter.order, BlocksAction(action), filter.sortBy)

            sortedAction.distinct
              .take(getFilterLimit(filter))
              .result
              .map(actions => actions.map(Tables.BlocksRow.tupled))
        }.getOrElse(
          DBIO.failed(new Exception("You can only filter blocks by block ID, level, chain ID, protocol, operation ID, operation source, or inner and outer operation kind."))
        )
    }

    dbHandle.run(filteringIO)
  }

  /**
    * Fetch a given operation group
    *
    * Runing the returned operation will fail with [[NoSuchElementException]] if
    *  - no block is found on the db
    *  - no group corresponds to the given hash
    *
    * @param operationGroupHash Operation group hash
    * @return Operation group along with associated operations and accounts
    */
  def fetchOperationGroup(operationGroupHash: String)(implicit ec: ExecutionContext): Future[Map[String, Any]] = {
    val groupedOpsIO = latestBlockIO().collect { // we fail the operation if no block is there
      case Some(_) =>
        TezosDatabaseOperations.operationsForGroupIO(operationGroupHash).map(_.get) // we want to fail here too
    }.flatten

    //convert to a valid object for the caller
    dbHandle.run(groupedOpsIO).map {
      case (opGroup, operations) =>
        Map(
          "operation_group" -> opGroup,
          "operations" -> operations
        )
    }
  }

  private def extractFromOperationGroup(opGroup: Tables.OperationGroups) = {
    (opGroup.protocol, opGroup.chainId, opGroup.hash, opGroup.branch,
      opGroup.signature, opGroup.blockId)
  }

  /**
    * Fetches all operation groups.
    * @param filter Filters to apply
    * @return List of operation groups
    */
  def fetchOperationGroups(filter: Filter)(implicit ec: ExecutionContext): Future[Seq[Tables.OperationGroupsRow]] = {
    val filteringIO = filteredTablesIO(filter).flatMap {
      filtered =>

        val blockFlag = isBlockFilter(filter)
        val operationGroupFlag = true
        val operationFlag = isOperationFilter(filter)
        val accountFlag = isAccountFilter(filter)
        val joinedTables = getJoinedTables(blockFlag, operationGroupFlag, operationFlag, accountFlag, filtered, filter)

        //there will be some action only if the joined tables have the expected shape
        val validAction = joinedTables.collect {

          case OperationGroups(operationGroups) =>
            for {
              opGroup <- operationGroups
            } yield extractFromOperationGroup(opGroup)

          case BlocksOperationGroups(blocksOperationGroups) =>
            for {
              (_, opGroup) <- blocksOperationGroups
            } yield extractFromOperationGroup(opGroup)

          case OperationGroupsOperations(operationGroupsOperations) =>
            for {
              (opGroup, _) <- operationGroupsOperations
            } yield extractFromOperationGroup(opGroup)

          case OperationGroupsAccounts(operationGroupsAccounts) =>
            for {
              (opGroup, _) <- operationGroupsAccounts
            } yield extractFromOperationGroup(opGroup)

          case OperationGroupsOperationsAccounts(operationGroupsOperationsAccounts) =>
            for {
              ((opGroup, _), _) <- operationGroupsOperationsAccounts
            } yield extractFromOperationGroup(opGroup)

          case BlocksOperationGroupsOperations(blocksOperationGroupsOperations) =>
            for {
              ((_, opGroup), _) <- blocksOperationGroupsOperations
            } yield extractFromOperationGroup(opGroup)

          case BlocksOperationGroupsOperationsAccounts(blocksOperationGroupsOperationsAccounts) =>
            for {
              (((_, opGroup), _), _) <- blocksOperationGroupsOperationsAccounts
            } yield  extractFromOperationGroup(opGroup)

        }

        validAction.map {
          action =>
            val OperationGroupsAction(sortedAction) = fetchSortedAction(filter.order, OperationGroupsAction(action), filter.sortBy)

            sortedAction.distinct
              .take(getFilterLimit(filter))
              .result
              .map(actions => actions.map(Tables.OperationGroupsRow.tupled))
        }.getOrElse(
          DBIO.failed(new Exception("This exception should never be reached, but is included for completeness."))
        )
      }

    dbHandle.run(filteringIO)
  }

  /**
    * Fetches all operations.
    * @param filter Filters to apply
    * @return List of operations
    */
  def fetchOperations(filter: Filter): Try[Seq[Tables.OperationsRow]] = Try{
    val action = for {
      o <- Tables.Operations
      og <- Tables.OperationGroups
      b <- Tables.Blocks
      if o.operationGroupHash === og.hash &&
      og.blockId === b.hash &&
      filterOperationIDs(filter, o) &&
      filterOperationSources(filter, o) &&
      filterOperationDestinations(filter, o) &&
      filterOperationParticipants(filter, o) &&
      filterOperationKinds(filter, o) &&
      filterBlockIDs(filter, b) &&
      filterBlockLevels(filter, b) &&
      filterChainIDs(filter, b) &&
      filterProtocols(filter, b)
    } yield (
      o.kind,
      o.source,
      o.amount,
      o.destination,
      o.balance,
      o.delegate,
      o.operationGroupHash,
      o.operationId,
      o.fee,
      o.storageLimit,
      o.gasLimit,
      o.blockHash,
      o.timestamp,
      o.blockLevel
      )
    val op = dbHandle.run(
      action.
        distinct.
        sortBy(_._14.desc).
        take(getFilterLimit(filter)).
        result
    )
    val results = Await.result(op, Duration.apply(awaitTimeInSeconds, SECONDS))
    results.map(x => Tables.OperationsRow(
      x._1, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9, x._10, x._11, x._12, x._13, x._14)
    )
  }



  /**
    * Given the operation kind and the number of columns wanted,
    * return the mean (along with +/- one standard deviation) of
    * fees incurred in those operations.
    * @param filter Filters to apply, specifically operation kinds
    * @return AverageFee class, getting low, medium, and high
    *         estimates for average fees, timestamp the calculation
    *         was performed at, and the kind of operation being
    *         averaged over.
    */
  def fetchAverageFees(filter: Filter): Try[AverageFees] = Try {
    val action = for {
      fee <- Tables.Fees
      if filterOperationKindsForFees(filter, fee)
    } yield (fee.low, fee.medium, fee.high, fee.timestamp, fee.kind)
    val op = dbHandle.run(action.distinct.sortBy(_._4.desc).take(1).result)
    val results = Await.result(op, Duration.apply(awaitTimeInSeconds, SECONDS)).head
    AverageFees(results._1, results._2, results._3, results._4, results._5)
  }

  /**
    * Fetches the level of the most recent block in the accounts table.
    *
    * @return Max level or -1 if no blocks were found in the database.
    */
  def fetchMaxBlockLevelForAccounts(): Try[BigDecimal] = Try {
    val op = dbHandle.run(Tables.Accounts.map(_.blockLevel).max.result)
    val maxLevelOpt = Await.result(op, Duration.apply(awaitTimeInSeconds, SECONDS))
    maxLevelOpt match {
      case Some(maxLevel) => maxLevel
      case None => -1
    }
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
        val account = Await.result(op, Duration.apply(awaitTimeInSeconds, SECONDS)).head
        Map("account" -> account)
      }
    }

  /**
    * Fetches a list of accounts from the db.
    * @param filter Filters to apply
    * @return       List of accounts
    */
  def fetchAccounts(filter: Filter)(implicit ec: ExecutionContext) = {
    val filteringIO = filteredTablesIO(filter).flatMap {
      filtered =>

        val blockFlag = isBlockFilter(filter)
        val operationGroupFlag = isOperationGroupFilter(filter)
        val operationFlag = isOperationFilter(filter)
        val accountFlag = true
        val joinedTables = getJoinedTables(blockFlag, operationGroupFlag, operationFlag, accountFlag, filtered, filter)

        //there will be some action only if the joined tables have the expected shape
        val validAction = joinedTables.collect {

          case Accounts(accounts) =>
            for {
              a <- accounts
            } yield (a.accountId, a.blockId, a.manager, a.spendable, a.delegateSetable, a.delegateValue, a.counter, a.script, a.balance, a.blockLevel)

          case OperationGroupsAccounts(operationGroupsAccounts) =>
            for {
              (_, a) <- operationGroupsAccounts
            } yield (a.accountId, a.blockId, a.manager, a.spendable, a.delegateSetable, a.delegateValue, a.counter, a.script, a.balance, a.blockLevel)

          case OperationGroupsOperationsAccounts(operationGroupsOperationsAccounts) =>
            for {
              ((_, _), a) <- operationGroupsOperationsAccounts
            } yield (a.accountId, a.blockId, a.manager, a.spendable, a.delegateSetable, a.delegateValue, a.counter, a.script, a.balance, a.blockLevel)

        }

        validAction.map {
          action =>
            val AccountsAction(sortedAction) = fetchSortedAction(filter.order, AccountsAction(action), filter.sortBy)

            sortedAction.distinct
              .take(getFilterLimit(filter))
              .result
              .map(actions => actions.map(Tables.AccountsRow.tupled))
        }.getOrElse(
          DBIO.failed(new Exception("You can only filter accounts by operation ID, operation source, account ID, account manager, account delegate, or inner and outer operation kind."))
        )
    }

    dbHandle.run(filteringIO)
  }

  /**
    * @return the most recent block, if one exists in the database.
    */
  private[tezos] def latestBlockIO()(implicit ec: ExecutionContext): DBIO[Option[BlocksRow]] =
    TezosDb.maxBlockLevel.flatMap(
      maxLevel =>
        Tables.Blocks
          .filter(_.level === maxLevel)
          .take(1)
          .result
          .headOption
    )

}

