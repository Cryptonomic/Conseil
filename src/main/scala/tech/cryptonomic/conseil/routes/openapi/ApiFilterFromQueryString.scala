package tech.cryptonomic.conseil.routes.openapi

import cats.Functor
import cats.syntax.functor._
import endpoints.algebra
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter

/** Trait containing helper functions which are necessary for parsing query parameter strings as Filter  */
trait ApiFilterFromQueryString extends QueryStringLists { self: algebra.JsonSchemaEntities =>
  import tech.cryptonomic.conseil.routes.openapi.TupleFlattenHelper._
  import FlattenHigh._

  /** Query string functor adding map operation */
  implicit def qsFunctor: Functor[QueryString]

  /** Query params type alias */
  type QueryParams = (
    Option[Int],
      List[String],
      List[Int],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      Option[String],
      Option[String]
    )

  /** Function for extracting query string with query params */
  private def filterQs: QueryString[QueryParams] = {
    val raw =
      optQs[Int]("limit") &
        qsList[String]("block_id") &
        qsList[Int]("block_level") &
        qsList[String]("block_netid") &
        qsList[String]("block_protocol") &
        qsList[String]("operation_id") &
        qsList[String]("operation_source") &
        qsList[String]("operation_destination") &
        qsList[String]("operation_participant") &
        qsList[String]("operation_kind") &
        qsList[String]("account_id") &
        qsList[String]("account_manager") &
        qsList[String]("account_delegate") &
        optQs[String]("sort_by") &
        optQs[String]("order")
    raw map (flatten(_))
  }

  /** Function for mapping query string to Filter */
  val qsFilter: QueryString[Filter] =
    filterQs.map(
      (Filter.readParams _).tupled
    )

}
