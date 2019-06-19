package tech.cryptonomic.conseil.util

import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{GetResult, PositionedParameters, SQLActionBuilder}
import tech.cryptonomic.conseil.generic.chain.DataTypes.AggregationType.AggregationType
import tech.cryptonomic.conseil.generic.chain.DataTypes.OperationType.OperationType
import tech.cryptonomic.conseil.generic.chain.DataTypes._

/**
  * Utility functions and members for common database operations.
  */
object DatabaseUtil {
  lazy val db = Database.forConfig("conseildb")

  /**
    * Utility object for generic query composition with SQL interpolation
    */
  object QueryBuilder {
    /** Concatenates SQLActionsBuilders
      * Slick does not support easy concatenation of actions so we need this function based on https://github.com/slick/slick/issues/1161
      *
      * @param acc     base SqlActionBuilder with which we want concatenate other actions
      * @param actions list of actions to concatenate
      * @return one SQLActionBuilder containing concatenated actions
      */
    def concatenateSqlActions(acc: SQLActionBuilder, actions: SQLActionBuilder*): SQLActionBuilder = {
      actions.foldLeft(acc) {
        case (accumulator, action) =>
          SQLActionBuilder(accumulator.queryParts ++ action.queryParts, (p: Unit, pp: PositionedParameters) => {
            accumulator.unitPConv.apply(p, pp)
            action.unitPConv.apply(p, pp)
          })
      }
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
      (1 to positionedResult.numColumns).map(i => {
        val columnName = metadata.getColumnName(i).toLowerCase
        val columnValue = positionedResult.nextObjectOption
        columnName -> columnValue
      }).toMap
    })

    /** Implicit class providing helper methods for SQLActionBuilder */
    implicit class SqlActionHelper(action: SQLActionBuilder) {
      /** Method for adding predicates to existing SQLAction
        *
        * @param predicates list of predicates to add
        * @return new SQLActionBuilder containing given predicates
        */
      def addPredicates(predicates: List[Predicate]): SQLActionBuilder = {
        concatenateSqlActions(action, makePredicates(predicates):_*)
      }

      /** Method for adding ordering to existing SQLAction
        *
        * @param ordering list of QueryOrdering to add
        * @return new SQLActionBuilder containing ordering statements
        */
      def addOrdering(ordering: List[QueryOrdering]): SQLActionBuilder = {
        val queryOrdering = if (ordering.isEmpty) {
          List.empty
        } else {
          List(makeOrdering(ordering))
        }
        concatenateSqlActions(action, queryOrdering:_*)
      }

      /** Method for adding limit to existing SQLAction
        *
        * @param limit limit to add
        * @return new SQLActionBuilder containing limit statement
        */
      def addLimit(limit: Int): SQLActionBuilder = {
        concatenateSqlActions(action, makeLimit(limit))
      }

      /** Method for adding group by to existing SQLAction
        *
        * @param aggregation parameter containing info about field which has to be aggregated
        * @param columns     parameter containing columns which chich are being used in query
        * @return new SQLActionBuilder containing limit statement
        */
      def addGroupBy(aggregation: List[Aggregation], columns: List[String]): SQLActionBuilder = {
        val aggregationFields = aggregation.map(_.field).toSet
        val existingAggregationFields = columns.toSet.diff(aggregationFields).toList
        if(aggregation.isEmpty || existingAggregationFields.isEmpty) {
          action
        } else {
          concatenateSqlActions(action, makeGroupBy(existingAggregationFields))
        }
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
          predicate.precision.map(precision => sql""" AND ROUND(#${predicate.field}, $precision) """)
            .getOrElse(sql""" AND #${predicate.field} """),
          mapOperationToSQL(predicate.operation, predicate.inverse, predicate.set.map(_.toString))
        )
      }

    /** Prepares query
      *
      * @param table   table on which query will be executed
      * @param columns columns which are selected from teh table
      * @param aggregation parameter containing info about field which has to be aggregated
      * @return SQLAction with basic query
      */
    def makeQuery(table: String, columns: List[String], aggregation: List[Aggregation]): SQLActionBuilder = {
      val aggregationFields = aggregation.map(aggr => mapAggregationToSQL(aggr.function, aggr.field))
      val aggr = aggregationFields ::: columns.toSet.diff(aggregation.map(_.field).toSet).toList
      val cols = if (columns.isEmpty) "*" else aggr.mkString(",")
      sql"""SELECT #$cols FROM #$table WHERE true """
    }

    /** Prepares ordering parameters
      *
      * @param ordering list of ordering parameters
      * @return SQLAction with ordering
      */
    def makeOrdering(ordering: List[QueryOrdering]): SQLActionBuilder = {
      val orderingBy = ordering.map(ord => s"${ord.field} ${ord.direction}").mkString(",")
      sql""" ORDER BY #$orderingBy"""
    }

    /** Prepares limit parameters
      *
      * @param limit list of ordering parameters
      * @return SQLAction with ordering
      */
    def makeLimit(limit: Int): SQLActionBuilder = {
      sql""" LIMIT $limit"""
    }

    /** Prepares group by parameters
      *
      * @param columns list of columns to be grouped
      * @return SQLAction with group by
      */
    def makeGroupBy(columns: List[String]): SQLActionBuilder = {
      sql""" GROUP BY #${columns.mkString(",")}"""
    }

    /** maps aggregation operation to the SQL function*/
    private def mapAggregationToSQL(aggregationType: AggregationType, column: String): String = {
      aggregationType match {
        case AggregationType.sum => s"SUM($column) as sum_$column"
        case AggregationType.count => s"COUNT($column) as count_$column"
        case AggregationType.max => s"MAX($column) as max_$column"
        case AggregationType.min => s"MIN($column) as min_$column"
        case AggregationType.avg => s"AVG($column) as avg_$column"
      }
    }

    /** maps operation type to SQL operation */
    private def mapOperationToSQL(operation: OperationType, inverse: Boolean, vals: List[String]): SQLActionBuilder = {
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
      if(inverse) {
        concatenateSqlActions(op, sql" IS #${!inverse}")
      } else {
        op
      }
    }
  }

}
