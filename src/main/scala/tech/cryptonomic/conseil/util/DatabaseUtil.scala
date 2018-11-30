package tech.cryptonomic.conseil.util

import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{PositionedParameters, SQLActionBuilder}

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
}
