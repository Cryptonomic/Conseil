package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.generic

import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

/**
  * Represents a query filter submitted to the Conseil API.
  *
  * @param limit                  How many records to return
  * @param blockIds               Block IDs
  * @param transactionIDs         Transaction IDs
  * @param sortBy                 Database column name to sort by
  * @param order                  Sort items ascending or descending
  */
final case class BitcoinFilter(
    limit: Option[Int] = Some(defaultLimit),
    blockIds: Set[String] = Set.empty,
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
          set = blockIds.toList
        ),
        Predicate(
          field = "txid",
          operation = OperationType.in,
          set = transactionIDs.toList
        )
      ).filter(_.set.nonEmpty),
      limit = limit.getOrElse(DataTypes.defaultLimitValue),
      orderBy = toQueryOrdering(sortBy, order).toList,
      snapshot = None
    )
}
