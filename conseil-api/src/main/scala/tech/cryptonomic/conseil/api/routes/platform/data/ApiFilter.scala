package tech.cryptonomic.conseil.api.routes.platform.data

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{OrderDirection, QueryOrdering}

trait ApiFilter {

  /** Define sorting order for api queries */
  sealed trait Sorting extends Product with Serializable
  case object AscendingSort extends Sorting
  case object DescendingSort extends Sorting
  object Sorting {

    /** Read an input string (`asc` or `desc`) to return a
      * (possible) [[ApiFilter.Sorting]] value
      */
    def fromString(s: String): Option[Sorting] = s.toLowerCase match {
      case "asc" => Some(AscendingSort)
      case "desc" => Some(DescendingSort)
      case _ => None
    }

    /** Read an input [[ApiFilter.Sorting]] and converts to [[String]] */
    def asString(s: Sorting): String = s match {
      case AscendingSort => "asc"
      case DescendingSort => "desc"
    }
  }

  // default limit on output results, if not available as call input
  val defaultLimit = 10

  def toQueryOrdering(sortBy: Option[String], order: Option[Sorting]): Option[QueryOrdering] =
    sortBy.map { field =>
      val direction = order match {
        case Some(AscendingSort) => OrderDirection.asc
        case _ => OrderDirection.desc
      }
      QueryOrdering(field, direction)
    }

}

object ApiFilter extends ApiFilter
