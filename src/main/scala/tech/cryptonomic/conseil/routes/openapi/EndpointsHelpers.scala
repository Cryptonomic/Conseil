package tech.cryptonomic.conseil.routes.openapi

import cats.Functor
import cats.syntax.functor._
import endpoints.algebra
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter

/** Trait containing helper functions which are necessary for parsing query parameter strings as Filter  */
trait EndpointsHelpers extends QueryStringLists with algebra.JsonSchemaEntities with Validation with TupleFlattenHelper {
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
  def filterQs: QueryString[QueryParams] = {
    val raw =
      optQs[Int]("limit") &
        qsList[String]("blockIDs") &
        qsList[Int]("levels") &
        qsList[String]("chainIDs") &
        qsList[String]("protocols") &
        qsList[String]("operationGroupIDs") &
        qsList[String]("operationSources") &
        qsList[String]("operationDestinations") &
        qsList[String]("operationParticipants") &
        qsList[String]("operationKinds") &
        qsList[String]("accountIDs") &
        qsList[String]("accountManagers") &
        qsList[String]("accountDelegates") &
        optQs[String]("sortBy") &
        optQs[String]("order")
    raw map (flatten(_))
  }

  /** Function for mapping query string to Filter */
  val queryStringFilter: QueryString[Filter] =
    filterQs.map(
      (Filter.readParams _).tupled
    )

}
