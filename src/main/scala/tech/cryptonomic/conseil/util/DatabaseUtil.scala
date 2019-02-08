package tech.cryptonomic.conseil.util

import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{GetResult, PositionedParameters, SQLActionBuilder}
import tech.cryptonomic.conseil.generic.chain.DataTypes.OperationType.OperationType
import tech.cryptonomic.conseil.generic.chain.DataTypes.{OperationType, Predicate, QueryOrdering}

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
      * @param  xs list of values to be inserted into SQLAction
      * @return SqlActionBuilder with values from parameter
      */
    def insertValuesIntoSqlAction[T](xs: Seq[T]): SQLActionBuilder = {
      var b = sql"("
      var first = true
      xs.foreach { x =>
        if (first) first = false
        else b = concatenateSqlActions(b, sql",")
        b = concatenateSqlActions(b, sql"'#$x'")
      }
      concatenateSqlActions(b, sql")")
    }

    /** Implicit value that allows getting table row as Map[String, Any] */
    implicit val getMap: GetResult[Map[String, Option[Any]]] = GetResult[Map[String, Option[Any]]](positionedResult => {
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
      * @return SQLAction with basic query
      */
    def makeQuery(table: String, columns: List[String]): SQLActionBuilder = {
      val cols = if (columns.isEmpty) "*" else columns.mkString(",")
      sql"""SELECT #$cols FROM #$table WHERE true """
    }

    /** Prepares ordering parameters
      *
      * @param ordering list of ordering parameters
      * @return SQLAction with ordering
      */
    def makeOrdering(ordering: List[QueryOrdering]): SQLActionBuilder = {
      val orderingBy = ordering.map(x => s"${x.field} ${x.direction}").mkString(",")
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

    /** maps operation type to SQL operation */
    private def mapOperationToSQL(operation: OperationType, inverse: Boolean, vals: List[String]): SQLActionBuilder = {
      val op = operation match {
        case OperationType.between => sql"BETWEEN #${vals.head} AND #${vals(1)}"
        case OperationType.in => concatenateSqlActions(sql"IN ", insertValuesIntoSqlAction(vals))
        case OperationType.like => sql"LIKE '%#${vals.head}%'"
        case OperationType.lt | OperationType.before => sql"< '#${vals.head}'"
        case OperationType.gt | OperationType.after => sql"> '#${vals.head}'"
        case OperationType.eq => sql"= '#${vals.head}'"
        case OperationType.startsWith => sql"LIKE '#${vals.head}%'"
        case OperationType.endsWith => sql"LIKE '%#${vals.head}'"
        case OperationType.isnull => sql"ISNULL"
      }
      concatenateSqlActions(op, sql" IS #${!inverse}")
    }
  }

}
