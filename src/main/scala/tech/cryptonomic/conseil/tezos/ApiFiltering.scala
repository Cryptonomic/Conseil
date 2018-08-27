package tech.cryptonomic.conseil.tezos

import slick.jdbc.PostgresProfile.api._
import ApiOperations._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.higherKinds
import scala.util.Try

object ApiFiltering {

  case class TableSelection(
    blocks: Boolean,
    operationGroups: Boolean,
    operations: Boolean,
    accounts: Boolean
  )

  // default limit on output results, if not available as call input
  private val defaultLimit = 10

  private[this] def nonEmpty(subFilter: Option[Set[_]]) = subFilter.exists(_.nonEmpty)

  // Predicates to determine existence of specific type of filter

  /** Will the filter affect blocks? */
  def isBlockFilter(filter: Filter): Boolean =
    nonEmpty(filter.blockIDs) ||
      nonEmpty(filter.levels) ||
      nonEmpty(filter.chainIDs) ||
      nonEmpty(filter.protocols)

  /** Will the filter affect operation groups? */
  def isOperationGroupFilter(filter: Filter): Boolean =
    nonEmpty(filter.operationGroupIDs) ||
      nonEmpty(filter.operationSources)

  /** Will the filter affect operations? */
  def isOperationFilter(filter: Filter): Boolean =
    nonEmpty(filter.operationKinds) ||
      nonEmpty(filter.operationSources) ||
      nonEmpty(filter.operationDestinations)

  /** Will the filter affect accounts? */
  def isAccountFilter(filter: Filter): Boolean =
    nonEmpty(filter.accountDelegates) ||
      nonEmpty(filter.accountIDs) ||
      nonEmpty(filter.accountManagers)

  /* always get a limit to results */
  def getFilterLimit(filter: Filter): Int = filter.limit.getOrElse(defaultLimit)

  /** A collection of functions to get pre-filtered queries */
  object Queries {
    import slick.jdbc.PostgresProfile.api._

    //building blocks

    private[this] def filterBlockIDs(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
      filter.blockIDs.fold(ifEmpty = true.bind)(set => b.hash.inSet(set))

    private[this] def filterBlockLevels(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
      filter.levels.fold(ifEmpty = true.bind)(set => b.level.inSet(set))

    private[this] def filterChainIDs(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
      filter.chainIDs.fold(ifEmpty = true.bind)(set => b.chainId.getOrElse("").inSet(set))

    private[this] def filterProtocols(filter: Filter, b: Tables.Blocks): Rep[Boolean] =
      filter.protocols.fold(ifEmpty = true.bind)(set => b.protocol.inSet(set))

    private[this] def filterOperationIDs(filter: Filter, og: Tables.OperationGroups): Rep[Boolean] =
      filter.operationGroupIDs.fold(ifEmpty = true.bind)(set => og.hash.inSet(set))

    private[this] def filterOperationIDs(filter: Filter, o: Tables.Operations): Rep[Boolean] =
      filter.operationGroupIDs.fold(ifEmpty = true.bind)(set => o.operationGroupHash.inSet(set))

    private[this] def filterOperationSources(filter: Filter, o: Tables.Operations): Rep[Boolean] =
      filter.operationSources.fold(ifEmpty = true.bind)(set => o.source.getOrElse("").inSet(set))

    private[this] def filterOperationDestinations(filter: Filter, o: Tables.Operations): Rep[Boolean] =
      filter.operationDestinations.fold(ifEmpty = true.bind)(set => o.destination.getOrElse("").inSet(set))

    private[this] def filterOperationParticipants(filter: Filter, o: Tables.Operations): Rep[Boolean] =
      filter.operationParticipants.fold(ifEmpty = true.bind)(set => o.destination.getOrElse(o.source.getOrElse("")).inSet(set))

    private[this] def filterAccountIDs(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
      filter.accountIDs.fold(ifEmpty = true.bind)(set => a.accountId.inSet(set))

    private[this] def filterAccountManagers(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
      filter.accountManagers.fold(ifEmpty = true.bind)(set => a.manager.inSet(set))

    private[this] def filterAccountDelegates(filter: Filter, a: Tables.Accounts): Rep[Boolean] =
      filter.accountDelegates.fold(ifEmpty = true.bind)(set => a.delegateValue.getOrElse("").inSet(set))

    private[this] def filterOperationKinds(filter: Filter, o: Tables.Operations): Rep[Boolean] =
      filter.operationKinds.fold(ifEmpty = true.bind)(set => o.kind.inSet(set))

    private[this] def filterOperationKindsForFees(filter: Filter, fee: Tables.Fees): Rep[Boolean] =
      filter.operationKinds.fold(ifEmpty = true.bind)(set => fee.kind.inSet(set))

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
        filterOperationSources(appliedFilters, op)
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

/**
  * type class to define complex filtering operations for specific tables
  * @tparam F   An effect that the output will be wrapped into, e.g. [[Try]], [[scala.concurrent.Future]]
  * @tparam OUT The specific output value or each result row
  */
trait ApiFiltering[F[_], OUT] {

  import ApiFiltering.{TableSelection, getFilterLimit}
  import ApiFiltering.Queries._

  /**
    * Applies filtering
    * @param filter              A [[Filter]] instance
    * @param maxLevelForAccounts How far in the chain we have accounts for
    * @return                    A sequence of filtered results wrapped in the effect `F`
    */
  def apply(filter: Filter)(maxLevelForAccounts: BigDecimal): F[Seq[OUT]] = {
    val joinTables: TableSelection => JoinedTables = prepareJoins(filter, maxLevelForAccounts)
    val execute: JoinedTables => F[Seq[OUT]] = executeQuery(getFilterLimit(filter), filter.order, filter.sortBy)
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
    * @param limit       Cap on the result sequence
    * @param fieldToSort Possibly picks a sorting field
    * @param sortBy      The sorting order as a String
    * @return            The actual results
    */
  protected def executeQuery(
    limit: Int,
    fieldToSort: Option[String],
    sortBy: Option[String]
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
trait ActionSorting[A <: Action] {
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
    * @param fieldToSort Picks a field to sort on
    * @param sortBy      The sorting order
    * @param action      The input [[Action]]
    * @return            A copy of the input with sorting applied
    */
  def fetchSortedAction(fieldToSort: Option[String], sortBy: Option[String], action: A): A

}

trait ApiFilters {

  implicit object OperationGroupsFiltering$ extends ApiFiltering[Try, Tables.OperationGroupsRow] with ActionSorting[OperationGroupsAction] {

    import ApiFiltering._

    override def select(f: Filter): TableSelection =
      TableSelection(
        blocks = isBlockFilter(f),
        operationGroups = true,
        operations = isOperationFilter(f),
        accounts = isAccountFilter(f)
      )

    override protected def executeQuery(
      limit: Int,
      fieldToSort: Option[String],
      sortBy: Option[String]
    ): JoinedTables => Try[Seq[Tables.OperationGroupsRow]] =
      joinedTables =>
        Try {
          val action = joinedTables match {
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

            case _ =>
              throw new IllegalStateException("This exception should never be reached, but is included for completeness.")
          }

          val sortingField: Option[String] = fieldToSort.map(_.toLowerCase)

          val OperationGroupsAction(sortedAction) = fetchSortedAction(sortingField, sortBy, OperationGroupsAction(action))
          val op = dbHandle.run(sortedAction.distinct.take(limit).result)
          Await.result(op, awaitTimeInSeconds.seconds)

        }

    override def fetchSortedAction(
      fieldToSort: Option[String],
      sortBy: Option[String],
      action: OperationGroupsAction): OperationGroupsAction =
    {

      val column = fieldToSort.map(_.toLowerCase).map {
        case "protocol" => t: Tables.OperationGroups => sortingOn(t.protocol, sortBy)
        case "chain_id" => t: Tables.OperationGroups => sortingOn(t.chainId, sortBy)
        case "hash" => t: Tables.OperationGroups => sortingOn(t.hash, sortBy)
        case "branch" => t: Tables.OperationGroups => sortingOn(t.branch, sortBy)
        case "signature" => t: Tables.OperationGroups => sortingOn(t.signature, sortBy)
        case "block_id" => t: Tables.OperationGroups => sortingOn(t.blockId, sortBy)
      } getOrElse {
        t: Tables.OperationGroups => sortingOn(t.hash, sortBy)
      }

      action.copy(
        action = action.action.sortBy(column)
      )
    }

  }
}

object ApiFilters extends ApiFilters
