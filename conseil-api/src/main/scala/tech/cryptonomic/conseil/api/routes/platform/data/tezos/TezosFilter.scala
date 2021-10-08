package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

/**
  * Represents a query filter submitted to the Conseil API.
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
final case class TezosFilter(
    limit: Option[Int] = Some(defaultLimit),
    blockIDs: Set[String] = Set.empty,
    levels: Set[Int] = Set.empty,
    chainIDs: Set[String] = Set.empty,
    protocols: Set[String] = Set.empty,
    operationGroupIDs: Set[String] = Set.empty,
    operationSources: Set[String] = Set.empty,
    operationDestinations: Set[String] = Set.empty,
    operationParticipants: Set[String] = Set.empty,
    operationKinds: Set[String] = Set.empty,
    accountIDs: Set[String] = Set.empty,
    accountManagers: Set[String] = Set.empty,
    accountDelegates: Set[String] = Set.empty,
    sortBy: Option[String] = None,
    order: Option[Sorting] = Some(DescendingSort)
) {

  /** transforms Filter into a Query with a set of predicates */
  def toQuery: DataTypes.Query =
    Query(
      fields = List.empty,
      predicates = List(
        Predicate(
          field = "block_id",
          operation = OperationType.in,
          set = blockIDs.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "level",
          operation = OperationType.in,
          set = levels.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "chain_id",
          operation = OperationType.in,
          set = chainIDs.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "protocol",
          operation = OperationType.in,
          set = protocols.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "level",
          operation = OperationType.in,
          set = levels.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "group_id",
          operation = OperationType.in,
          set = operationGroupIDs.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "source",
          operation = OperationType.in,
          set = operationSources.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "destination",
          operation = OperationType.in,
          set = operationDestinations.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "participant",
          operation = OperationType.in,
          set = operationParticipants.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "kind",
          operation = OperationType.in,
          set = operationKinds.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "account_id",
          operation = OperationType.in,
          set = accountIDs.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "manager",
          operation = OperationType.in,
          set = accountManagers.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate(
          field = "delegate",
          operation = OperationType.in,
          set = accountDelegates.toList,
          inverse = false,
          precision = None,
          group = None
        )
      ).filter(_.set.nonEmpty),
      limit = limit.getOrElse(DataTypes.defaultLimitValue),
      orderBy = toQueryOrdering(sortBy, order).toList,
      snapshot = None,
      aggregation = List.empty[Aggregation],
      temporalPartition = None,
      output = OutputType.json
    )
}
