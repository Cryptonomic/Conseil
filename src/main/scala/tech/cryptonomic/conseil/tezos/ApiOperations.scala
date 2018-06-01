package tech.cryptonomic.conseil.tezos

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos
import tech.cryptonomic.conseil.tezos.Tables.AccountsRow
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try
import java.sql.Timestamp


/**
  * Functionality for fetching data from the Conseil database.
  */
object ApiOperations {

  lazy val dbHandle: Database = DatabaseUtil.db

  /**
    * Repesents a query filter submitted to the Conseil API.
    *
    * @param limit                How many records to return
    * @param blockIDs             Block IDs
    * @param levels               Block levels
    * @param chainIDs             Chain IDs
    * @param protocols            Protocols
    * @param operationIDs         Operation IDs
    * @param operationSources     Operation sources
    * @param accountIDs           Account IDs
    * @param accountManagers      Account managers
    * @param accountDelegates     Account delegates
    * @param operationKinds       Operation outer kind
    * @param operationGroupKinds  Operation inner kind
    * @param sortBy               Database column name to sort by
    * @param order                Sort items ascending or descending
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
                     operationGroupKinds: Option[Set[String]] = Some(Set[String]()),
                     sortBy: Option[String] = None,
                     order: Option[String] = Some("DESC")
                   )

  // Represents queries for all tables after filters have been applied to them
  case class FilteredTables(
                             filteredAccounts: Query[Tables.Accounts, Tables.Accounts#TableElementType, Seq],
                             filteredBlocks: Query[Tables.Blocks, Tables.Blocks#TableElementType, Seq],
                             filteredOperationGroups: Query[Tables.OperationGroups, Tables.OperationGroups#TableElementType, Seq],
                             filteredOperations: Query[Tables.Operations, Tables.Operations#TableElementType, Seq]
                           )

  // Represents queries for all possible joins
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

  // Represents types for all possible sorted slick actions
  sealed trait Action

  case class BlocksAction
  (action: Query[(Rep[String], Rep[String], Rep[Int], Rep[Int], Rep[String], Rep[Int], Rep[String], Rep[String], Rep[String], Rep[Timestamp], Rep[String], Rep[Option[String]]),
                 (String, String, Int, Int, String, Int, String, String, String, Timestamp, String, Option[String]),
                 Seq]) extends Action

  case class OperationGroupsAction
  (action: Query [(Rep[String], Rep[String], Rep[Option[String]], Rep[Option[String]], Rep[Option[Int]], Rep[Option[String]], Rep[Option[String]], Rep[Option[String]],
                    Rep[Option[BigDecimal]], Rep[Option[String]], Rep[Option[String]], Rep[Option[String]], Rep[Option[String]], Rep[Option[BigDecimal]], Rep[Option[String]], Rep[String]),
                  (String, String, Option[String], Option[String], Option[Int], Option[String], Option[String], Option[String], Option[BigDecimal], Option[String], Option[String],
                    Option[String], Option[String], Option[BigDecimal], Option[String], String),
                  Seq]) extends Action

  case class AccountsAction
  (action: Query [(Rep[String], Rep[String], Rep[String], Rep[Boolean], Rep[Boolean], Rep[Option[String]], Rep[Int], Rep[Option[String]], Rep[BigDecimal]),
                  (String, String, String, Boolean, Boolean, Option[String], Int, Option[String], BigDecimal),
                  Seq]) extends Action

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


  // Filter all possible tables based on user input
  private def getFilteredTables(filter: Filter): Try[FilteredTables] = {
    fetchLatestBlock().flatMap { latestBlock =>
      Try {

        val filteredAccounts = Tables.Accounts.filter({ account =>
            filterAccountIDs(filter, account) &&
            filterAccountDelegates(filter, account) &&
            filterAccountManagers(filter, account) &&
            account.blockId === latestBlock.hash })

        val filteredOpGroups = Tables.OperationGroups.filter({ opGroup =>
            filterOperationIDs(filter, opGroup) &&
            filterOperationSources(filter, opGroup) &&
            filterOperationGroupKinds(filter, opGroup) })

        val filteredOps = Tables.Operations.filter({ op => filterOperationKinds(filter, op) })

        val filteredBlocks = Tables.Blocks.filter({ block =>
            filterBlockIDs(filter, block) &&
            filterBlockLevels(filter, block) &&
            filterChainIDs(filter, block) &&
            filterProtocols(filter, block)})

        FilteredTables(filteredAccounts, filteredBlocks, filteredOpGroups, filteredOps)
      }
    }
  }

  // Return appropriate joined table based on user request and filter, if possible
  private def getJoinedTables(blockFlag: Boolean,
                              operationGroupFlag: Boolean,
                              operationFlag: Boolean,
                              accountFlag: Boolean,
                              tables: FilteredTables): Option[JoinedTables] = {

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
        Some(OperationGroupsOperationsAccounts(operationGroups
          .join(operations).on(_.hash === _.operationGroupHash)
          .join(accounts).on(_._1.source === _.accountId)))
      case (false, false, true, false) =>
        None
      case (false, false, false, true) =>
        Some(Accounts(accounts))
      case (false, false, false, false) =>
        None
    }
  }

  // this will be refactored out later, initial solution to user wanting to sort by columns in current schema
  // will break if schema changes
  private def fetchSortedAction(order: Option[String], action: Action, sortBy: Option[String]): Action = {

    //default is sorting descending by block level, operation group hash, and account ID, otherwise order and sorting column chosen by user
    val sortedAction =
      action match {
        case BlocksAction(blockAction) =>
          sortBy match {
            case Some(x) if x.toLowerCase() == "chain_id" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._1.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._1.desc))
                case None => BlocksAction(blockAction.sortBy(_._1.desc))
              }
            case Some(x) if x.toLowerCase() == "protocol" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._2.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._2.desc))
                case None => BlocksAction(blockAction.sortBy(_._2.desc))
              }
            case Some(x) if x.toLowerCase() == "level" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._3.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._3.desc))
                case None => BlocksAction(blockAction.sortBy(_._3.desc))
              }
            case Some(x) if x.toLowerCase() == "proto" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._4.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._4.desc))
                case None => BlocksAction(blockAction.sortBy(_._4.desc))
              }
            case Some(x) if x.toLowerCase() == "predecessor" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._5.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._5.desc))
                case None => BlocksAction(blockAction.sortBy(_._5.desc))
              }
            case Some(x) if x.toLowerCase() == "validation_pass" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._6.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._6.desc))
                case None => BlocksAction(blockAction.sortBy(_._6.desc))
              }
            case Some(x) if x.toLowerCase() == "operations_hash" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._7.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._7.desc))
                case None => BlocksAction(blockAction.sortBy(_._7.desc))
              }
            case Some(x) if x.toLowerCase() == "protocol_data" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._8.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._8.desc))
                case None => BlocksAction(blockAction.sortBy(_._8.desc))
              }
            case Some(x) if x.toLowerCase() == "hash" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._9.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._9.desc))
                case None => BlocksAction(blockAction.sortBy(_._9.desc))
              }
            case Some(x) if x.toLowerCase() == "timestamp" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._10.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._10.desc))
                case None => BlocksAction(blockAction.sortBy(_._10.desc))
              }
            case Some(x) if x.toLowerCase() == "fitness" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._11.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._11.desc))
                case None => BlocksAction(blockAction.sortBy(_._11.desc))
              }
            case Some(x) if x.toLowerCase() == "context" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._12.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._12.desc))
                case None => BlocksAction(blockAction.sortBy(_._12.desc))
              }
            case None =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => BlocksAction(blockAction.sortBy(_._3.asc))
                case Some(x) if x.toLowerCase() == "desc" => BlocksAction(blockAction.sortBy(_._3.desc))
                case None => BlocksAction(blockAction.sortBy(_._3.desc))
              }
          }
        case OperationGroupsAction(opGroupAction) =>
          sortBy match {
            case Some(x) if x.toLowerCase() == "hash" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._1.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._1.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._1.desc))
              }
            case Some(x) if x.toLowerCase() == "branch" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._2.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._2.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._2.desc))
              }
            case Some(x) if x.toLowerCase() == "kind" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._3.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._3.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._3.desc))
              }
            case Some(x) if x.toLowerCase() == "block" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._4.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._4.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._4.desc))
              }
            case Some(x) if x.toLowerCase() == "level" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._5.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._5.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._5.desc))
              }
            case Some(x) if x.toLowerCase() == "slots" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._6.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._6.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._6.desc))
              }
            case Some(x) if x.toLowerCase() == "signature" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._7.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._7.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._7.desc))
              }
            case Some(x) if x.toLowerCase() == "proposals" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._8.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._8.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._8.desc))
              }
            case Some(x) if x.toLowerCase() == "period" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._9.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._9.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._9.desc))
              }
            case Some(x) if x.toLowerCase() == "source" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._10.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._10.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._10.desc))
              }
            case Some(x) if x.toLowerCase() == "proposal" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._11.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._11.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._11.desc))
              }
            case Some(x) if x.toLowerCase() == "ballot" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._12.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._12.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._12.desc))
              }
            case Some(x) if x.toLowerCase() == "chain" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._13.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._13.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._13.desc))
              }
            case Some(x) if x.toLowerCase() == "counter" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._14.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._14.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._14.desc))
              }
            case Some(x) if x.toLowerCase() == "fee" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._15.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._15.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._15.desc))
              }
            case Some(x) if x.toLowerCase() == "block_id" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._16.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._16.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._16.desc))
              }
            case None =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => OperationGroupsAction(opGroupAction.sortBy(_._1.asc))
                case Some(x) if x.toLowerCase() == "desc" => OperationGroupsAction(opGroupAction.sortBy(_._1.desc))
                case None => OperationGroupsAction(opGroupAction.sortBy(_._1.desc))
              }
          }
        case AccountsAction(accountAction) =>
          sortBy match {
            case Some(x) if x.toLowerCase() == "account_id" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => AccountsAction(accountAction.sortBy(_._1.asc))
                case Some(x) if x.toLowerCase() == "desc" => AccountsAction(accountAction.sortBy(_._1.desc))
                case None => AccountsAction(accountAction.sortBy(_._1.desc))
              }
            case Some(x) if x.toLowerCase() == "block_id" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => AccountsAction(accountAction.sortBy(_._2.asc))
                case Some(x) if x.toLowerCase() == "desc" => AccountsAction(accountAction.sortBy(_._2.desc))
                case None => AccountsAction(accountAction.sortBy(_._2.desc))
              }
            case Some(x) if x.toLowerCase() == "manager" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => AccountsAction(accountAction.sortBy(_._3.asc))
                case Some(x) if x.toLowerCase() == "desc" => AccountsAction(accountAction.sortBy(_._3.desc))
                case None => AccountsAction(accountAction.sortBy(_._3.desc))
              }
            case Some(x) if x.toLowerCase() == "spendable" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => AccountsAction(accountAction.sortBy(_._4.asc))
                case Some(x) if x.toLowerCase() == "desc" => AccountsAction(accountAction.sortBy(_._4.desc))
                case None => AccountsAction(accountAction.sortBy(_._4.desc))
              }
            case Some(x) if x.toLowerCase() == "delegate_setable" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => AccountsAction(accountAction.sortBy(_._5.asc))
                case Some(x) if x.toLowerCase() == "desc" => AccountsAction(accountAction.sortBy(_._5.desc))
                case None => AccountsAction(accountAction.sortBy(_._5.desc))
              }
            case Some(x) if x.toLowerCase() == "delegate_value" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => AccountsAction(accountAction.sortBy(_._6.asc))
                case Some(x) if x.toLowerCase() == "desc" => AccountsAction(accountAction.sortBy(_._6.desc))
                case None => AccountsAction(accountAction.sortBy(_._6.desc))
              }
            case Some(x) if x.toLowerCase() == "counter" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => AccountsAction(accountAction.sortBy(_._7.asc))
                case Some(x) if x.toLowerCase() == "desc" => AccountsAction(accountAction.sortBy(_._7.desc))
                case None => AccountsAction(accountAction.sortBy(_._7.desc))
              }
            case Some(x) if x.toLowerCase() == "script" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => AccountsAction(accountAction.sortBy(_._8.asc))
                case Some(x) if x.toLowerCase() == "desc" => AccountsAction(accountAction.sortBy(_._8.desc))
                case None => AccountsAction(accountAction.sortBy(_._8.desc))
              }
            case Some(x) if x.toLowerCase() == "balance" =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => AccountsAction(accountAction.sortBy(_._9.asc))
                case Some(x) if x.toLowerCase() == "desc" => AccountsAction(accountAction.sortBy(_._9.desc))
                case None => AccountsAction(accountAction.sortBy(_._9.desc))
              }
            case None =>
              order match {
                case Some(x) if x.toLowerCase() == "asc" => AccountsAction(accountAction.sortBy(_._1.asc))
                case Some(x) if x.toLowerCase() == "desc" => AccountsAction(accountAction.sortBy(_._1.desc))
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

        // Blocks need to be fetch, other tables needed if user asks for them via the filter
        val blockFlag = true
        val operationGroupFlag = isOperationGroupFilter(filter)
        val operationFlag = isOperationFilter(filter)
        val accountFlag = isAccountFilter(filter)
        val joinedTables = getJoinedTables(blockFlag, operationGroupFlag, operationFlag, accountFlag, filteredTables)

        val action = joinedTables match {

          case Some(Blocks(blocks)) =>
            for {
              b <- blocks
            } yield (b.chainId, b.protocol, b.level, b.proto, b.predecessor, b.validationPass, b.operationsHash, b.protocolData, b.hash, b.timestamp, b.fitness, b.context)

          case Some(BlocksOperationGroups(blocksOperationGroups)) =>
            for {
              (b, _) <- blocksOperationGroups
            } yield (b.chainId, b.protocol, b.level, b.proto, b.predecessor, b.validationPass, b.operationsHash, b.protocolData, b.hash, b.timestamp, b.fitness, b.context)

          case Some(BlocksOperationGroupsOperations(blocksOperationGroupsOperations)) =>
            for {
              ((b, _), _) <- blocksOperationGroupsOperations
            } yield (b.chainId, b.protocol, b.level, b.proto, b.predecessor, b.validationPass, b.operationsHash, b.protocolData, b.hash, b.timestamp, b.fitness, b.context)

          case _ =>
            throw new Exception("You can only filter blocks by block ID, level, chain ID, protocol, operation ID, operation source, or inner and outer operation kind.")

        }

        val BlocksAction(sortedAction) = fetchSortedAction(filter.order, BlocksAction(action), filter.sortBy)
        val op = dbHandle.run(sortedAction.distinct.take(getFilterLimit(filter)).result)
        val results = Await.result(op, Duration.Inf)
        results.map(x => Tables.BlocksRow(x._1, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9, x._10, x._11, x._12))
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

          case _ =>
            throw new Exception("This exception should never be reached, but is included for completeness.")
        }

        val OperationGroupsAction(sortedAction) = fetchSortedAction(filter.order, OperationGroupsAction(action), filter.sortBy)
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
    getFilteredTables(filter).flatMap { filteredTables =>

      Try {

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
            throw new Exception("You can only filter accounts by operation ID, operation source, account ID, account manager, account delegate, or inner and outer operation kind.")
        }

        val AccountsAction(sortedAction) = fetchSortedAction(filter.order, AccountsAction(action), filter.sortBy)
        val op = dbHandle.run(sortedAction.distinct.take(getFilterLimit(filter)).result)
        val results = Await.result(op, Duration.Inf)
        results.map(x => Tables.AccountsRow(x._1, x._2, x._3, x._4, x._5, x._6, x._7, x._8, x._9))

      }

    }

  }

}


