package tech.cryptonomic.conseil.util

import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{GetResult, PositionedParameters, SQLActionBuilder}

/**
  * Utility functions and members for common database operations.
  */
object DatabaseUtil {
  lazy val db = Database.forConfig("conseildb")

  //some adjusted hacks from https://github.com/slick/slick/issues/1161 as Slick does not have simple concatenation of actions
  /** concatenates SQLActionsBuilders */
  def concat(acc: SQLActionBuilder, actions: List[SQLActionBuilder]): SQLActionBuilder = {
    actions.foldLeft(acc) {
      case (accumulator, action) =>
        SQLActionBuilder(accumulator.queryParts ++ action.queryParts, (p: Unit, pp: PositionedParameters) => {
          accumulator.unitPConv.apply(p, pp)
          action.unitPConv.apply(p, pp)
        })
    }
  }

  /** inserts values into query */
  def values[T](xs: TraversableOnce[T]): SQLActionBuilder = {
    var b = sql"("
    var first = true
    xs.foreach { x =>
      if(first) first = false
      else b = concat(b, List(sql","))
      b = concat(b, List(sql"'#$x'"))
    }
    concat(b, List(sql")"))
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

}
