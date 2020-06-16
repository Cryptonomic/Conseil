package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import tech.cryptonomic.conseil.api.routes.platform.data.tezos.TezosFilter._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

/**
  * Represents a query filter submitted to the Conseil API.
  *
  * @param limit                  How many records to return
  * @param sortBy                 Database column name to sort by
  * @param order                  Sort items ascending or descending
  */
final case class BitcoinFilter(
    limit: Option[Int] = Some(defaultLimit),
    sortBy: Option[String] = None,
    order: Option[Sorting] = Some(DescendingSort)
) {

  /** transforms Filter into a Query with a set of predicates */
  def toQuery: DataTypes.Query =
    Query(
      fields = List.empty,
      predicates = List[Predicate](
        //TODO Add predicates here later on
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


