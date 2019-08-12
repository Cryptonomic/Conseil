package tech.cryptonomic.conseil.util

import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{GetResult, PositionedParameters, SQLActionBuilder}
import tech.cryptonomic.conseil.generic.chain.DataTypes.AggregationType.AggregationType
import tech.cryptonomic.conseil.generic.chain.DataTypes.OperationType.OperationType
import tech.cryptonomic.conseil.generic.chain.DataTypes._
import tech.cryptonomic.conseil.tezos.Tables

/**
  * Utility functions and members for common database operations.
  */
object DatabaseUtil {
  lazy val db = Database.forConfig("conseildb")

  trait QueryBuilder[+T <: Table[_]] {
    def makeQuery(
        table: String,
        columns: List[String],
        aggregation: List[Aggregation]
    ): SQLActionBuilder

    /* use this to know how the blocks invalidation table is named*/
    protected lazy val invalidatedTableName =
      Tables.InvalidatedBlocks.baseTableRow.tableName

    /* use this to know how the invalidation column in the corresponding table is named*/
    protected lazy val invalidatedColumnName =
      Tables.InvalidatedBlocks.baseTableRow.isInvalidated.column.toString

    /* use this to know how the foreign key to blocks in the invalidation table is named*/
    protected lazy val blockFKColumnName =
      Tables.InvalidatedBlocks.baseTableRow.hash.column.toString

  }

  /**
    * Utility object for generic query composition with SQL interpolation
    */
  object QueryBuilder {

    def fullyQualifyColumn(table: String) = (col: String) => s"$table.$col"
    def fullyQualifyPredicate(table: String) =
      (predicate: Predicate) => predicate.copy(field = fullyQualifyColumn(table)(predicate.field))

    /** Concatenates SQLActionsBuilders
      * Slick does not support easy concatenation of actions so we need this function based on https://github.com/slick/slick/issues/1161
      *
      * @param acc     base SqlActionBuilder with which we want concatenate other actions
      * @param actions list of actions to concatenate
      * @return one SQLActionBuilder containing concatenated actions
      */
    def concatenateSqlActions(acc: SQLActionBuilder, actions: SQLActionBuilder*): SQLActionBuilder =
      actions.foldLeft(acc) {
        case (accumulator, action) =>
          SQLActionBuilder(accumulator.queryParts ++ action.queryParts, (p: Unit, pp: PositionedParameters) => {
            accumulator.unitPConv.apply(p, pp)
            action.unitPConv.apply(p, pp)
          })
      }

    /** Creates SQLAction of sequence of values
      *
      * @param  values list of values to be inserted into SQLAction
      * @return SqlActionBuilder with values from parameter
      */
    def insertValuesIntoSqlAction[T](values: Seq[T]): SQLActionBuilder = {
      @scala.annotation.tailrec
      def append(content: SQLActionBuilder, values: List[T]): SQLActionBuilder = values match {
        case Nil =>
          concatenateSqlActions(content, sql")")
        case head :: tail =>
          val next = concatenateSqlActions(content, sql",", sql"'#$head'")
          append(next, tail)
      }

      if (values.isEmpty) sql"()"
      else append(sql"('#${values.head}'", values.tail.toList)
    }

    /** Implicit value that allows getting table row as Map[String, Any] */
    implicit val getMap: GetResult[QueryResponse] = GetResult[QueryResponse](positionedResult => {
      val metadata = positionedResult.rs.getMetaData
      (1 to positionedResult.numColumns)
        .map(i => {
          val columnName = metadata.getColumnName(i).toLowerCase
          val columnValue = positionedResult.nextObjectOption
          columnName -> columnValue
        })
        .toMap
    })

    /** Implicit class providing helper methods for SQLActionBuilder */
    implicit class SqlActionHelper(action: SQLActionBuilder) {

      /** Method for adding predicates to existing SQLAction
        *
        * @param table the main table name
        * @param predicates list of predicates to add
        * @return new SQLActionBuilder containing given predicates
        */
      def addPredicates(table: String, predicates: List[Predicate]): SQLActionBuilder =
        concatenateSqlActions(action, makePredicates(predicates.map(fullyQualifyPredicate(table))): _*)

      /** Method for adding ordering to existing SQLAction
        *
        * @param table the main table name
        * @param ordering list of QueryOrdering to add
        * @param nonAggregateFields fields not used for aggregate values
        * @return new SQLActionBuilder containing ordering statements
        */
      def addOrdering(
          table: String,
          ordering: List[QueryOrdering],
          nonAggregateFields: Set[String]
      ): SQLActionBuilder = {
        val qualify = fullyQualifyColumn(table)
        if (ordering.isEmpty) {
          action
        } else {
          val qualified = ordering.collect {
            case q @ QueryOrdering(field, dir) if nonAggregateFields(field) => q.copy(field = qualify(field))
            case q => q
          }
          concatenateSqlActions(action, makeOrdering(qualified))
        }
      }

      /** Method for adding limit to existing SQLAction
        *
        * @param limit limit to add
        * @return new SQLActionBuilder containing limit statement
        */
      def addLimit(limit: Int): SQLActionBuilder =
        concatenateSqlActions(action, makeLimit(limit))

      /** Method for adding GROUP BY to existing SQLAction
        *
        * @param table the main table name
        * @param aggregation parameter containing info about field which has to be aggregated
        * @param columns     parameter containing columns which chich are being used in query
        * @return new SQLActionBuilder containing GROUP BY  statement
        */
      def addGroupBy(table: String, aggregation: List[Aggregation], columns: List[String]): SQLActionBuilder = {
        val qualify = fullyQualifyColumn(table)
        val aggregationFields = aggregation.map(_.field).toSet
        val columnsWithoutAggregationFields = columns.toSet.diff(aggregationFields).map(qualify).toList
        if (aggregation.isEmpty || columnsWithoutAggregationFields.isEmpty) {
          action
        } else {
          concatenateSqlActions(action, makeGroupBy(columnsWithoutAggregationFields))
        }
      }

      /** Method for adding HAVING to existing SQLAction
        *
        * @param aggregation parameter containing info about field which has to be aggregated
        * @return new SQLActionBuilder containing HAVING statement
        */
      def addHaving(table: String, aggregation: List[Aggregation]): SQLActionBuilder = {
        val qualify = fullyQualifyColumn(table)

        if (aggregation.exists(_.getPredicate.nonEmpty))
          concatenateSqlActions(action, makeHaving(qualify)(aggregation))
        else action
      }

    }

    /** Prepares predicates and transforms them into SQLActionBuilders
      *
      * @param  predicates list of predicates to be transformed
      * @return list of transformed predicates
      */
    def makePredicates(predicates: List[Predicate]): List[SQLActionBuilder] =
      predicates.map { predicate =>
        concatenateSqlActions(
          predicate.precision
            .map(precision => sql""" AND ROUND(#${predicate.field}, $precision) """)
            .getOrElse(sql""" AND #${predicate.field} """),
          mapOperationToSQL(predicate.operation, predicate.inverse, predicate.set.map(_.toString))
        )
      }

    /** Prepares query
      *
      * @param table   table on which query will be executed
      * @param columns columns which are selected from teh table
      * @param aggregation parameter containing info about field which has to be aggregated
      * @param builder is an implicit instance needed to actually make the query
      * @tparam T is a type placeholder for a real table definition needed to find a query builder
      * @return SQLAction with basic query
      */
    def makeQuery[T <: Table[_]](
        table: String,
        columns: List[String],
        aggregation: List[Aggregation]
    )(implicit builder: QueryBuilder[T]): SQLActionBuilder =
      builder.makeQuery(table, columns, aggregation)

    /** Prepares ordering parameters
      *
      * @param ordering list of ordering parameters
      * @return SQLAction with ordering
      */
    def makeOrdering(ordering: List[QueryOrdering]): SQLActionBuilder = {
      val orderingBy = ordering.map(ord => s"${ord.field} ${ord.direction}").mkString(", ")
      sql""" ORDER BY #$orderingBy"""
    }

    /** Prepares limit parameters
      *
      * @param limit list of ordering parameters
      * @return SQLAction with ordering
      */
    def makeLimit(limit: Int): SQLActionBuilder =
      sql""" LIMIT #$limit"""

    /** Prepares group by parameters
      *
      * @param columns list of columns to be grouped
      * @return SQLAction with group by
      */
    def makeGroupBy(columns: List[String]): SQLActionBuilder =
      sql""" GROUP BY #${columns.mkString(", ")}"""

    /** Prepares HAVING parameters
      *
      * @param aggregation list of aggregations
      * @return SQLAction with HAVING
      */
    def makeHaving(qualify: String => String)(aggregation: List[Aggregation]): SQLActionBuilder =
      concatenateSqlActions(
        sql""" HAVING true""",
        aggregation.flatMap { aggregation =>
          aggregation.getPredicate.toList.map { predicate =>
            concatenateSqlActions(
              sql""" AND #${mapAggregationToSQL(qualify)(aggregation.function, aggregation.field)} """,
              mapOperationToSQL(predicate.operation, predicate.inverse, predicate.set.map(_.toString))
            )
          }
        }: _*
      )

    /** maps aggregation operation to the SQL function*/
    def mapAggregationToSQL(qualify: String => String)(aggregationType: AggregationType, column: String): String =
      aggregationType match {
        case AggregationType.sum => s"SUM(${qualify(column)})"
        case AggregationType.count => s"COUNT(${qualify(column)})"
        case AggregationType.max => s"MAX(${qualify(column)})"
        case AggregationType.min => s"MIN(${qualify(column)})"
        case AggregationType.avg => s"AVG(${qualify(column)})"
      }

    /** maps aggregation operation to the SQL alias */
    def mapAggregationToAlias(aggregationType: AggregationType, column: String): String =
      aggregationType match {
        case AggregationType.sum => s"sum_$column"
        case AggregationType.count => s"count_$column"
        case AggregationType.max => s"max_$column"
        case AggregationType.min => s"min_$column"
        case AggregationType.avg => s"avg_$column"
      }

    /** maps operation type to SQL operation */
    def mapOperationToSQL(operation: OperationType, inverse: Boolean, vals: List[String]): SQLActionBuilder = {
      val op = operation match {
        case OperationType.between => sql"BETWEEN '#${vals.head}' AND '#${vals(1)}'"
        case OperationType.in => concatenateSqlActions(sql"IN ", insertValuesIntoSqlAction(vals))
        case OperationType.like => sql"LIKE '%#${vals.head}%'"
        case OperationType.lt | OperationType.before => sql"< '#${vals.head}'"
        case OperationType.gt | OperationType.after => sql"> '#${vals.head}'"
        case OperationType.eq => sql"= '#${vals.head}'"
        case OperationType.startsWith => sql"LIKE '#${vals.head}%'"
        case OperationType.endsWith => sql"LIKE '%#${vals.head}'"
        case OperationType.isnull => sql"ISNULL"
      }
      if (inverse) {
        concatenateSqlActions(op, sql" IS #${!inverse}")
      } else {
        op
      }
    }
  }

}
