package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

/**
  * Represents a query filter submitted to the Conseil API.
  *
  * @param limit            How many records to return
  * @param blockIds         Block IDs
  * @param blockHashes      Block Hashes
  * @param transactionIDs   Transaction IDs
  * @param accountAddresses Account addresses
  * @param sortBy           Database column name to sort by
  * @param order            Sort items ascending or descending
  */
final case class BitcoinFilter(
    limit: Option[Int] = Some(defaultLimit),
    blockIds: Set[String] = Set.empty,
    blockHashes: Set[String] = Set.empty,
    transactionIDs: Set[String] = Set.empty,
    accountAddresses: Set[String] = Set.empty,
    sortBy: Option[String] = None,
    order: Option[Sorting] = Some(DescendingSort)
) {

  /** Transforms Filter into a Query with a set of predicates */
  def toQuery: DataTypes.Query =
    Query(
      fields = List.empty,
      predicates = List(
        Predicate( // to find specific block with hash
          field = "hash",
          operation = OperationType.in,
          set = blockIds.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate( // to find specific transaction with id
          field = "txid",
          operation = OperationType.in,
          set = transactionIDs.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate( // to find transactions by specific block_hash
          field = "blockhash",
          operation = OperationType.in,
          set = blockHashes.toList,
          inverse = false,
          precision = None,
          group = None
        ),
        Predicate( // to find accounts by specific address
          field = "address",
          operation = OperationType.in,
          set = accountAddresses.toList,
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
