package tech.cryptonomic.conseil.routes.openapi

import cats.Functor
import cats.syntax.functor._
import endpoints.algebra
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter

trait EndpointsHelpers extends QueryStringLists with algebra.JsonSchemaEntities with Validation with TupleFlattenHelper {
  import FlattenHigh._

  implicit def qsFunctor: Functor[QueryString]

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

  val queryStringFilter: QueryString[Filter] =
    filterQs.map(
      (Filter.readParams _).tupled
    )

}
