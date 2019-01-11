package tech.cryptonomic.conseil.util

import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{GetResult, PositionedParameters, SQLActionBuilder}
import tech.cryptonomic.conseil.generic.chain.DataTypes.{Predicate, QueryOrdering}
import tech.cryptonomic.conseil.tezos.TezosDatabaseOperations.{makePredicates, makeOrdering, makeLimit}

/**
  * Utility functions and members for common database operations.
  */
object DatabaseUtil {
  lazy val db = Database.forConfig("conseildb")

  /** Concatenates SQLActionsBuilders
    * Slick does not support easy concatenation of actions so we need this function based on https://github.com/slick/slick/issues/1161
    * @param acc      base SqlActionBuilder with which we want concatenate other actions
    * @param actions  list of actions to concatenate
    * @return         one SQLActionBuilder containing concatenated actions
    */
  def concatenateSqlActions(acc: SQLActionBuilder, actions: List[SQLActionBuilder]): SQLActionBuilder = {
    actions.foldLeft(acc) {
      case (accumulator, action) =>
        SQLActionBuilder(accumulator.queryParts ++ action.queryParts, (p: Unit, pp: PositionedParameters) => {
          accumulator.unitPConv.apply(p, pp)
          action.unitPConv.apply(p, pp)
        })
    }
  }

  /** Creates SQLAction of sequence of values
    * @param  xs  list of values to be inserted into SQLAction
    * @return     SqlActionBuilder with values from parameter
    */
  def insertValuesIntoSqlAction[T](xs: Seq[T]): SQLActionBuilder = {
    var b = sql"("
    var first = true
    xs.foreach { x =>
      if(first) first = false
      else b = concatenateSqlActions(b, List(sql","))
      b = concatenateSqlActions(b, List(sql"'#$x'"))
    }
    concatenateSqlActions(b, List(sql")"))
  }

  /** Implicit value that allows getting table row as Map[String, Any] */
  implicit val getMap: GetResult[Map[String, Any]] = GetResult[Map[String, Any]](positionedResult => {
    val metadata = positionedResult.rs.getMetaData
    (1 to positionedResult.numColumns).flatMap(i => {
      val columnName = metadata.getColumnName(i).toLowerCase
      val columnValue = positionedResult.nextObjectOption
      columnValue.map(columnName -> _)
    }).toMap
  })

  /** Implicit class providing helper methods for SQLActionBuilder */
  implicit class SqlActionHelper(action: SQLActionBuilder) {
    /** Method for adding predicates to existing SQLAction
      * @param predicates list of predicates to add
      * @return           new SQLActionBuilder containing given predicates
      */
    def addPredicates(predicates: List[Predicate]): SQLActionBuilder = {
      concatenateSqlActions(action, makePredicates(predicates))
    }

    /** Method for adding ordering to existing SQLAction
      * @param ordering   list of QueryOrdering to add
      * @return           new SQLActionBuilder containing ordering statements
      */
    def addOrdering(ordering: List[QueryOrdering]): SQLActionBuilder = {
      val queryOrdering = if(ordering.isEmpty) {
        List.empty
      } else {
        List(makeOrdering(ordering))
      }
      concatenateSqlActions(action, queryOrdering)
    }

    /** Method for adding limit to existing SQLAction
      * @param limit      limit to add
      * @return           new SQLActionBuilder containing limit statement
      */
    def addLimit(limit: Int): SQLActionBuilder = {
      concatenateSqlActions(action, List(makeLimit(limit)))
    }
  }

}
