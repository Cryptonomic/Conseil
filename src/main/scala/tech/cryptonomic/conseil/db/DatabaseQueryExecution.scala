package tech.cryptonomic.conseil.db

import tech.cryptonomic.conseil.tezos.{ApiFiltering, Tables, TezosDatabaseOperations}
import tech.cryptonomic.conseil.tezos.ApiOperations._

import slick.jdbc.PostgresProfile.api._
import scala.language.higherKinds
import scala.concurrent.Future

/** database-specific filter operations support */
object DatabaseQueryExecution {

  /* Represents all possible joins of tables that can be made from Accounts, Blocks, Operation Groups, and Operations.
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

  case class OperationGroupsAction(action: Query[Tables.OperationGroups, Tables.OperationGroupsRow, Seq]) extends Action

  case class AccountsAction(action: Query[Tables.Accounts, Tables.AccountsRow, Seq]) extends Action

  case class TableSelection(
    blocks: Boolean,
    operationGroups: Boolean,
    operations: Boolean,
    accounts: Boolean
  )

  /** A collection of functions to get pre-filtered queries */
  private[db] object Queries {
    import slick.jdbc.PostgresProfile.api._

    //building blocks

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

    /** gets filtered accounts */
    val filteredAccounts = (appliedFilters: Filter, maxLevel: Rep[BigDecimal]) =>
      Tables.Accounts.filter(account =>
        filterAccountIDs(appliedFilters, account) &&
        filterAccountDelegates(appliedFilters, account) &&
        filterAccountManagers(appliedFilters, account) &&
        account.blockLevel === maxLevel
      )

    /** gets filtered operation groups */
    val filteredOpGroups = (appliedFilters: Filter) =>
      Tables.OperationGroups.filter(opGroup =>
      filterOperationIDs(appliedFilters, opGroup)
      )

    /** gets filtered operations */
    val filteredOps = (appliedFilters: Filter) =>
      Tables.Operations.filter(op =>
        filterOperationKinds(appliedFilters, op) &&
        filterOperationDestinations(appliedFilters, op) &&
        filterOperationSources(appliedFilters, op) &&
        filterOperationParticipants(appliedFilters, op)
      )

    /** gets filtered blocks */
    val filteredBlocks = (appliedFilters: Filter) =>
      Tables.Blocks.filter(block =>
        filterBlockIDs(appliedFilters, block) &&
        filterBlockLevels(appliedFilters, block) &&
        filterChainIDs(appliedFilters, block) &&
        filterProtocols(appliedFilters, block)
      )
  }

}

/** specific type class for running filtered queries on DB */
trait DatabaseQueryExecution[F[_], OUT] extends ApiFiltering[F, OUT] {

  import ApiFiltering.getFilterLimit
  import DatabaseQueryExecution._
  import DatabaseQueryExecution.Queries._

  /** See [[ApiFiltering#apply]] */
  def apply(filter: Filter)(maxLevelForAccounts: BigDecimal): F[Seq[OUT]] = {
    val joinTables: TableSelection => JoinedTables = prepareJoins(filter, maxLevelForAccounts)
    val execute: JoinedTables => F[Seq[OUT]] = executeQuery(getFilterLimit(filter), filter.sortBy, filter.order)
    (execute compose joinTables compose select)(filter)
  }

  // utilities to make the signatures more linear
  private def unwrapQuadJoin[A, B, C, D](nest: (((A, B), C), D)): (A, B, C, D) = nest match {
    case (((a, b), c), d) => (a, b, c, d)
  }

  private def unwrapTripleJoin[A, B, C](nest: ((A, B), C)): (A, B, C) = nest match {
    case ((a, b), c) => (a, b, c)
  }

  /**
    * Defines which tables are affected by this filter
    * @param filter The generic input [[Filter]] for the request
    * @return       Tables the filter will act on
    */
  protected def select(filter: Filter): TableSelection

  /**
    * Defines a function of [[JoinedTables]] that will actually execute all the queries
    * @param limit     Cap on the result sequence
    * @param sortBy    The sorting column as a String
    * @param sortOrder A sorting order
    * @return          The actual results
    */
  protected def executeQuery(
    limit: Int,
    sortBy: Option[String],
    sortOrder: Option[String]
  ): JoinedTables => F[Seq[OUT]]

  /**
    * Composes the actual queries to execute by joining tables
    * @param f        The generic input [[Filter]] for the request
    * @param maxLevel How far in the chain we have accounts for
    * @param s        Which tables the filter acts upon
    * @return         One of the available joins defined through the [[JoinedTables]] ADT
    */
  protected def prepareJoins(f: Filter, maxLevel: BigDecimal)(s: TableSelection): JoinedTables = {
    s match {
      case TableSelection(true, true, true, true) =>
        BlocksOperationGroupsOperationsAccounts(
          filteredBlocks(f)
            .join(filteredOpGroups(f)).on(_.hash === _.blockId)
            .join(filteredOps(f)).on(_._2.hash === _.operationGroupHash)
            .join(filteredAccounts(f, maxLevel)).on(_._2.source === _.accountId)
            .map(unwrapQuadJoin)
        )
      case TableSelection(true, true, true, false) =>
        BlocksOperationGroupsOperations(
          filteredBlocks(f)
            .join(filteredOpGroups(f)).on(_.hash === _.blockId)
            .join(filteredOps(f)).on(_._2.hash === _.operationGroupHash)
            .map(unwrapTripleJoin)
        )
      case TableSelection(true, true, false, true) =>
        EmptyJoin
      case TableSelection(true, true, false, false) =>
        BlocksOperationGroups(
          filteredBlocks(f).join(filteredOpGroups(f)).on(_.hash === _.blockId)
        )
      case TableSelection(true, false, true, true)
           | TableSelection(true, false, true, false)
           | TableSelection(true, false, false, true) =>
        EmptyJoin
      case TableSelection(true, false, false, false) =>
        Blocks(filteredBlocks(f))
      case TableSelection(false, true, true, true) =>
        OperationGroupsOperationsAccounts(
          filteredOpGroups(f)
            .join(filteredOps(f)).on(_.hash === _.operationGroupHash)
            .join(filteredAccounts(f, maxLevel)).on(_._2.source === _.accountId)
            .map(unwrapTripleJoin)
        )
      case TableSelection(false, true, true, false) =>
        OperationGroupsOperations(
          filteredOpGroups(f).join(filteredOps(f)).on(_.hash === _.operationGroupHash)
        )
      case TableSelection(false, true, false, true) =>
        OperationGroupsOperationsAccounts(
          filteredOpGroups(f)
            .join(filteredOps(f)).on(_.hash === _.operationGroupHash)
            .join(filteredAccounts(f, maxLevel)).on(_._2.source === _.accountId)
            .map(unwrapTripleJoin)
        )
      case TableSelection(false, true, false, false) =>
        OperationGroups(filteredOpGroups(f))
      case TableSelection(false, false, true, true) =>
        OperationGroupsOperationsAccounts(
          filteredOpGroups(f)
            .join(filteredOps(f)).on(_.hash === _.operationGroupHash)
            .join(filteredAccounts(f, maxLevel)).on(_._2.source === _.accountId)
            .map(unwrapTripleJoin)
        )
      case TableSelection(false, false, true, false) =>
        EmptyJoin
      case TableSelection(false, false, false, true) =>
        Accounts(filteredAccounts(f, maxLevel))
      case TableSelection(false, false, false, false) =>
        EmptyJoin
    }

  }
}

/** Collects utilities to simplify sorting operations */
trait ActionSorting[A <: DatabaseQueryExecution.Action] {
  import slick.lifted.ColumnOrdered

  /**
    * Read a sorting order to create an ordering on columns
    * @param col   Identifies a specific column that can be sorted
    * @param order "asc" or "desc"
    * @tparam T    The column type
    * @return      The column with sorting order applied
    */
  protected def sortingOn[T](col: ColumnOrdered[T], order: Option[String]): ColumnOrdered[T] =
    order.map(_.toLowerCase) match {
      case Some("asc") => col.asc
      case _ => col.desc
    }

  /**
    * Return table query which is the sorted verion of action, based on database column name, sortBy, and the order.
    * This will be refactored out later, as this is just an initial solution to the user wanting to sort by columns
    * according to the current schema. This will break if the schema changes.
    *
    * @param sortBy Parameter to say what column to sort by.
    * @param order  Parameter to determine whether to sort in ascending or descending order.
    * @param action The query for the table we want to sort.
    */
  def fetchSortedAction(sortBy: Option[String], order: Option[String], action: A): A

}

trait DatabaseApiFiltering {

  import DatabaseQueryExecution._
  import ApiFiltering._

  /**
    * an implementation is required to make the async [[ApiFiltering]] instances available in the context work correctly
    * consider using the appropriate instance to compose database operations
    */
  def asyncApiFiltersExecutionContext: scala.concurrent.ExecutionContext

  /* common wrapper check for many implementations
   * verify that there are blocks in the database before running a [[DBIO]]
   * If none exists the call returns a failed DBIO
   */
  private[this] def ensuringBlocksExist[R](dbOperation: => DBIO[R]): DBIO[R] =
      TezosDatabaseOperations.doBlocksExist().flatMap {
        blocksStored =>
          if(blocksStored) dbOperation
          else DBIO.failed(new NoSuchElementException("No block data is currently available"))
      }(asyncApiFiltersExecutionContext)


  /** an instance to execute filtering and sorting for blocks, asynchronously */
  implicit object BlocksFiltering extends DatabaseQueryExecution[Future, Tables.BlocksRow] with ActionSorting[BlocksAction] {

    // Blocks need to be fetched, other tables needed if user asks for them via the filter
    override protected def select(filter: Filter): TableSelection =
      TableSelection(
        blocks = true,
        operationGroups = isOperationGroupFilter(filter),
        operations = isOperationFilter(filter),
        accounts = isAccountFilter(filter)
      )

    private[this] val extractActionFromJoins = (joinedTables: JoinedTables) =>
        //there will be some action only if the joined tables have the expected shape
      PartialFunction.condOpt(joinedTables) {
        case Blocks(blocks) => blocks

        case BlocksOperationGroups(blocksOperationGroups) =>
          blocksOperationGroups.map { case (b, _) => b }

        case BlocksOperationGroupsOperations(blocksOperationGroupsOperations) =>
          blocksOperationGroupsOperations.map { case (b, _, _) => b }
      }

    /** will fail the [[Future]] with [[NoSuchElementException]] if no block is in the chain */
    override protected def executeQuery(
      limit: Int,
      sortBy: Option[String],
      sortOrder: Option[String]
    ): JoinedTables => Future[Seq[Tables.BlocksRow]] =
      extractActionFromJoins andThen {
        case Some(validAction) =>
          ensuringBlocksExist {
            val BlocksAction(sortedAction) = fetchSortedAction(sortBy, sortOrder, BlocksAction(validAction))
            sortedAction.distinct
              .take(limit)
              .result
          }
        case _ =>
          //when the joins didn't have the expected shape
          DBIO.failed(new IllegalArgumentException("You can only filter blocks by block ID, level, chain ID, protocol, operation ID, operation source, or inner and outer operation kind."))
      } andThen (dbHandle.run)

    override def fetchSortedAction(
      sortBy: Option[String],
      order: Option[String],
      action: BlocksAction): BlocksAction = {

        val column = sortBy.map(_.toLowerCase).map {
          case "level" => t: Tables.Blocks => sortingOn(t.level, order)
          case "proto" => t: Tables.Blocks => sortingOn(t.proto, order)
          case "predecessor" => t: Tables.Blocks => sortingOn(t.predecessor, order)
          case "timestamp" => t: Tables.Blocks => sortingOn(t.timestamp, order)
          case "validation_pass" => t: Tables.Blocks => sortingOn(t.validationPass, order)
          case "fitness" => t: Tables.Blocks => sortingOn(t.fitness, order)
          case "context" => t: Tables.Blocks => sortingOn(t.context, order)
          case "signature" => t: Tables.Blocks => sortingOn(t.signature, order)
          case "protocol" => t: Tables.Blocks => sortingOn(t.protocol, order)
          case "chain_id" => t: Tables.Blocks => sortingOn(t.chainId, order)
          case "hash" => t: Tables.Blocks => sortingOn(t.hash, order)
          case "operations_hash" => t: Tables.Blocks => sortingOn(t.operationsHash, order)
        } getOrElse {
          t: Tables.Blocks => sortingOn(t.level, order)
        }

        action.copy(
          action = action.action.sortBy(column)
        )

    }

  }

  /** an instance to execute filtering and sorting for accounts, asynchronously */
  implicit object AccountsFiltering extends DatabaseQueryExecution[Future, Tables.AccountsRow] with ActionSorting[AccountsAction] {

    override protected def select(filter: Filter): TableSelection =
      TableSelection(
        blocks = isBlockFilter(filter),
        operationGroups = isOperationGroupFilter(filter),
        operations = isOperationFilter(filter),
        accounts = true
      )

    private[this] val extractActionFromJoins = (joinedTables: JoinedTables) =>
      //there will be some action only if the joined tables have the expected shape
      PartialFunction.condOpt(joinedTables) {
        case Accounts(accounts) => accounts

        case OperationGroupsAccounts(operationGroupsAccounts) =>
          operationGroupsAccounts.map(_._2)

        case OperationGroupsOperationsAccounts(operationGroupsOperationsAccounts) =>
          operationGroupsOperationsAccounts.map(_._3)
      }

    /** will fail the [[Future]] with [[NoSuchElementException]] if no block is in the chain */
    override protected def executeQuery(
      limit: Int,
      sortBy: Option[String],
      sortOrder: Option[String]
    ): JoinedTables => Future[Seq[Tables.AccountsRow]] =
      extractActionFromJoins andThen {
        case Some(validAction) =>
          ensuringBlocksExist {
            val AccountsAction(sortedAction) = fetchSortedAction(sortBy, sortOrder, AccountsAction(validAction))
            sortedAction.distinct
              .take(limit)
              .result
          }
        case _ =>
          //when the joins didn't have the expected shape
          DBIO.failed(new IllegalArgumentException("You can only filter accounts by operation ID, operation source, account ID, account manager, account delegate, or inner and outer operation kind."))
      } andThen (dbHandle.run)

    override def fetchSortedAction(
      sortBy: Option[String],
      order: Option[String],
      action: AccountsAction): AccountsAction = {

        val column = sortBy.map(_.toLowerCase).map {
          case "account_id" => t: Tables.Accounts => sortingOn(t.accountId, order)
          case "block_id" => t: Tables.Accounts => sortingOn(t.blockId, order)
          case "manager" => t: Tables.Accounts => sortingOn(t.manager, order)
          case "spendable" => t: Tables.Accounts => sortingOn(t.spendable, order)
          case "delegate_setable" => t: Tables.Accounts => sortingOn(t.delegateSetable, order)
          case "delegate_value" => t: Tables.Accounts => sortingOn(t.delegateValue, order)
          case "counter" => t: Tables.Accounts => sortingOn(t.counter, order)
          case "script" => t: Tables.Accounts => sortingOn(t.script, order)
          case "balance" => t: Tables.Accounts => sortingOn(t.balance, order)
        } getOrElse {
          t: Tables.Accounts => sortingOn(t.accountId, order)
        }

        action.copy(
          action = action.action.sortBy(column)
        )

    }

  }

  /** an instance to execute filtering and sorting for operation groups, asynchronously */
  implicit object OperationGroupsFiltering extends DatabaseQueryExecution[Future, Tables.OperationGroupsRow] with ActionSorting[OperationGroupsAction] {

    override def select(f: Filter): TableSelection =
      TableSelection(
        blocks = isBlockFilter(f),
        operationGroups = true,
        operations = isOperationFilter(f),
        accounts = isAccountFilter(f)
      )

    private[this] val extractActionFromJoins = (joinedTables: JoinedTables) =>
      //there will be some action only if the joined tables have the expected shape
      PartialFunction.condOpt(joinedTables) {
        case OperationGroups(operationGroups) =>
          operationGroups

        case BlocksOperationGroups(blocksOperationGroups) =>
          blocksOperationGroups.map(_._2)

        case OperationGroupsOperations(operationGroupsOperations) =>
          operationGroupsOperations.map(_._1)

        case OperationGroupsAccounts(operationGroupsAccounts) =>
          operationGroupsAccounts.map(_._1)

        case OperationGroupsOperationsAccounts(operationGroupsOperationsAccounts) =>
          operationGroupsOperationsAccounts.map(_._1)

        case BlocksOperationGroupsOperations(blocksOperationGroupsOperations) =>
          blocksOperationGroupsOperations.map(_._2)

        case BlocksOperationGroupsOperationsAccounts(blocksOperationGroupsOperationsAccounts) =>
          blocksOperationGroupsOperationsAccounts.map(_._2)
      }

    /** will fail the [[Future]] with [[NoSuchElementException]] if no block is in the chain */
    override protected def executeQuery(
      limit: Int,
      sortBy: Option[String],
      sortOrder: Option[String]
    ): JoinedTables => Future[Seq[Tables.OperationGroupsRow]] =
      extractActionFromJoins andThen {
        case Some(validAction) =>
          ensuringBlocksExist {
            val OperationGroupsAction(sortedAction) = fetchSortedAction(sortBy, sortOrder, OperationGroupsAction(validAction))
            sortedAction.distinct
              .take(limit)
              .result
          }
        case _ =>
          //when the joins didn't have the expected shape
          DBIO.failed(new IllegalStateException("This exception should never be reached, but is included for completeness."))
      } andThen(dbHandle.run)

    override def fetchSortedAction(
      sortBy: Option[String],
      order: Option[String],
      action: OperationGroupsAction): OperationGroupsAction = {

      val column = sortBy.map(_.toLowerCase).map {
        case "protocol" => t: Tables.OperationGroups => sortingOn(t.protocol, order)
        case "chain_id" => t: Tables.OperationGroups => sortingOn(t.chainId, order)
        case "hash" => t: Tables.OperationGroups => sortingOn(t.hash, order)
        case "branch" => t: Tables.OperationGroups => sortingOn(t.branch, order)
        case "signature" => t: Tables.OperationGroups => sortingOn(t.signature, order)
        case "block_id" => t: Tables.OperationGroups => sortingOn(t.blockId, order)
      } getOrElse {
        t: Tables.OperationGroups => sortingOn(t.hash, order)
      }

      action.copy(
        action = action.action.sortBy(column)
      )
    }

  }

  /** an instance to execute filtering and sorting for operations, asynchronously */
  implicit object OperationsFiltering extends DatabaseQueryExecution[Future, Tables.OperationsRow] {

    override def select(f: Filter): TableSelection =
      TableSelection(
        blocks = true,
        operationGroups = true,
        operations = true,
        accounts = false
      )

    private[this] val extractActionFromJoins = (joinedTables: JoinedTables) =>
      //there will be some action only if the joined tables have the expected shape
      PartialFunction.condOpt(joinedTables) {
        case BlocksOperationGroupsOperations(blocksOperationGroupsOperations) =>
          blocksOperationGroupsOperations.map(_._3)
      }

    /** will fail the [[Future]] with [[NoSuchElementException]] if no block is in the chain */
    override protected def executeQuery(
      limit: Int,
      sortBy: Option[String],
      sortOrder: Option[String]
    ): JoinedTables => Future[Seq[Tables.OperationsRow]] =
      extractActionFromJoins andThen {
        case Some(validAction) =>
          ensuringBlocksExist {
            validAction.distinct
              .sortBy(_.blockLevel.desc)
              .take(limit)
              .result
          }
        case _ =>
          //when the joins didn't have the expected shape
          DBIO.failed(new IllegalStateException("This exception should never be reached, but is included for completeness."))
      } andThen(dbHandle.run)

  }

  /** an instance to execute filtering and sorting for fees, asynchronously */
  implicit object FeesFiltering extends ApiFiltering[Future, Tables.FeesRow] {

    /** See [[ApiFiltering#apply]] */
    def apply(filter: Filter)(maxLevelForAccounts: BigDecimal): Future[Seq[Tables.FeesRow]] = {
      import DatabaseQueryExecution.Queries

      val action =
        Tables.Fees
          .filter (
            fee => Queries.filterOperationKindsForFees(filter, fee)
          )
          .distinct
          .sortBy(_.timestamp.desc)
          .take(1)
          .result

      dbHandle.run(action)
    }

  }

}
