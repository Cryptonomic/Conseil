package tech.cryptonomic.conseil.routes.openapi

import endpoints.{InvariantFunctor, algebra}
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter

trait EndpointsHelpers extends QueryStringLists with algebra.JsonSchemaEntities with Validation with TupleFlattenHelper {
  implicit def qsInvFunctor: InvariantFunctor[QueryString]

  val myQueryStringParams = filterQs.xmap[Filter](
    { filters =>
      val flattenedFilters = flatten(filters)
      val toFilter = (Filter.readParams _).tupled
      val f = toFilter(flattenedFilters)
      f
    }, {
      _: Filter => ???
    }
  )

  def filterQs = optQs[Int]("limit") &
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
}
