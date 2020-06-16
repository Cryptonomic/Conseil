package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

/**
  * Represents a query filter submitted to the Conseil API.
  *
  * @param limit                  How many records to return
  * @param blockIDs Block IDs
  * @param transactionIDs Transaction IDs
  * @param sortBy                 Database column name to sort by
  * @param order                  Sort items ascending or descending
  */
final case class BitcoinFilter(
    limit: Option[Int] = Some(defaultLimit),
    blockIDs: Set[String] = Set.empty,
    transactionIDs: Set[String] = Set.empty,
    sortBy: Option[String] = None,
    order: Option[Sorting] = Some(DescendingSort)
) {

  /** transforms Filter into a Query with a set of predicates */
  def toQuery: DataTypes.Query =
    Query(
      fields = List.empty,
      predicates = List[Predicate](
        Predicate(
          field = "hash",
          operation = OperationType.in,
          set = blockIDs.toList
        ),
        Predicate(
          field = "txid",
          operation = OperationType.in,
          set = blockIDs.toList
        )
      ).filter(_.set.nonEmpty),
      limit = limit.getOrElse(DataTypes.defaultLimitValue),
      orderBy = toQueryOrdering(sortBy, order).toList,
      snapshot = None
    )
}

object BitcoinFilter {

  /** builds a filter from incoming string-based parameters */
  def readParams(
      limit: Option[Int],
      blockIDs: Iterable[String],
      transactionIDs: Iterable[String],
      sortBy: Option[String],
      order: Option[String]
  ): BitcoinFilter =
    BitcoinFilter(limit, blockIDs.toSet, transactionIDs.toSet, sortBy, order.flatMap(Sorting.fromString))
}
