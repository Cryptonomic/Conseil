package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import tech.cryptonomic.conseil.api.routes.platform.data.tezos
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.Filter._
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
final case class Filter(
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
          set = blockIDs.toList
        ),
        Predicate(
          field = "level",
          operation = OperationType.in,
          set = levels.toList
        ),
        Predicate(
          field = "chain_id",
          operation = OperationType.in,
          set = chainIDs.toList
        ),
        Predicate(
          field = "protocol",
          operation = OperationType.in,
          set = protocols.toList
        ),
        Predicate(
          field = "level",
          operation = OperationType.in,
          set = levels.toList
        ),
        Predicate(
          field = "group_id",
          operation = OperationType.in,
          set = operationGroupIDs.toList
        ),
        Predicate(
          field = "source",
          operation = OperationType.in,
          set = operationSources.toList
        ),
        Predicate(
          field = "destination",
          operation = OperationType.in,
          set = operationDestinations.toList
        ),
        Predicate(
          field = "participant",
          operation = OperationType.in,
          set = operationParticipants.toList
        ),
        Predicate(
          field = "kind",
          operation = OperationType.in,
          set = operationKinds.toList
        ),
        Predicate(
          field = "account_id",
          operation = OperationType.in,
          set = accountIDs.toList
        ),
        Predicate(
          field = "manager",
          operation = OperationType.in,
          set = accountManagers.toList
        ),
        Predicate(
          field = "delegate",
          operation = OperationType.in,
          set = accountDelegates.toList
        )
      ).filter(_.set.nonEmpty),
      limit = limit.getOrElse(DataTypes.defaultLimitValue),
      orderBy = sortBy.map { o =>
        val direction = order match {
          case Some(AscendingSort) => OrderDirection.asc
          case _ => OrderDirection.desc
        }
        QueryOrdering(o, direction)
      }.toList,
      snapshot = None
    )
}

object Filter {

  /** Define sorting order for api queries */
  sealed trait Sorting extends Product with Serializable
  case object AscendingSort extends Sorting
  case object DescendingSort extends Sorting
  object Sorting {

    /** Read an input string (`asc` or `desc`) to return a
      * (possible) [[tezos.Filter.Sorting]] value
      */
    def fromString(s: String): Option[Sorting] = s.toLowerCase match {
      case "asc" => Some(AscendingSort)
      case "desc" => Some(DescendingSort)
      case _ => None
    }
  }

  /** builds a filter from incoming string-based parameters */
  def readParams(
      limit: Option[Int],
      blockIDs: Iterable[String],
      levels: Iterable[Int],
      chainIDs: Iterable[String],
      protocols: Iterable[String],
      operationGroupIDs: Iterable[String],
      operationSources: Iterable[String],
      operationDestinations: Iterable[String],
      operationParticipants: Iterable[String],
      operationKinds: Iterable[String],
      accountIDs: Iterable[String],
      accountManagers: Iterable[String],
      accountDelegates: Iterable[String],
      sortBy: Option[String],
      order: Option[String]
  ): Filter =
    Filter(
      limit,
      blockIDs.toSet,
      levels.toSet,
      chainIDs.toSet,
      protocols.toSet,
      operationGroupIDs.toSet,
      operationSources.toSet,
      operationDestinations.toSet,
      operationParticipants.toSet,
      operationKinds.toSet,
      accountIDs.toSet,
      accountManagers.toSet,
      accountDelegates.toSet,
      sortBy,
      order.flatMap(Sorting.fromString)
    )

  // Common values

  // default limit on output results, if not available as call input
  val defaultLimit = 10

}
