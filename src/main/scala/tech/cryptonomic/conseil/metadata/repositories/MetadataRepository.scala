package tech.cryptonomic.conseil.metadata.repositories

/** Defines general query capabitilities dynamically built on
  * the abstract concept of storage `Table`s, `Column`s
  * and `Value`s for each record of a column
  * The effect of the result is generic as well.
  */
trait MetadataRepository[Eff[_], Table, Column, Value] {
  import tech.cryptonomic.conseil.generic.chain.DataTypes.{Aggregation, Predicate, QueryOrdering}
  import tech.cryptonomic.conseil.generic.chain.DataTypes.OutputType.OutputType

  /** Counts number of rows in the given table
    * @param table  table
    * @return       amount of rows in the table
    */
  def countRows(table: Table): Eff[Int]

  /** Counts number of distinct elements by given table and column
    *
    * @param table  the table
    * @param column the column
    * @return       amount of distinct elements in given column
    */
  def countDistinct(table: Table, column: Column): Eff[Int]

  /** Selects distinct elements by given table and column
    *
    * @param table  the table
    * @param column the column
    * @return       distinct elements in given column as a list
    */
  def selectDistinct(table: Table, column: Column): Eff[List[Value]]

  /**
    * Selects distinct elements by given table and column with filter
    *
    * @param table          the table
    * @param column         the column
    * @param matchingString string which is being matched
    * @return               distinct elements in given column as a list
    */
  def selectDistinctLike(table: Table, column: Column, matchingString: String): Eff[List[Value]]

  /** Selects elements filtered by the predicates
    *
    * @param table        the table
    * @param columns      list of columns
    * @param predicates   list of predicates for query to be filtered with
    * @param ordering     list of ordering conditions for the query
    * @param aggregation  optional aggregation
    * @param OutputType   the output representation of the results, enumerated as `OutputType`
    * @param limit        max number of rows fetched
    * @return             list of map of [column, any], which represents list of rows as a map of column to possible value
    */
  def selectWithPredicates(
      table: Table,
      columns: List[Column],
      predicates: List[Predicate],
      ordering: List[QueryOrdering],
      aggregation: List[Aggregation],
      outputType: OutputType,
      limit: Int
  ): Eff[List[Map[Column, Option[Any]]]]

}
