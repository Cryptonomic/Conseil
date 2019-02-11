package tech.cryptonomic.conseil.routes.openapi

import endpoints.{InvariantFunctor, algebra}
import tech.cryptonomic.conseil.generic.chain.DataTypes.{ApiQuery, QueryValidationError}
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter
import tech.cryptonomic.conseil.tezos.Tables.BlocksRow


trait Endpoints
  extends algebra.Endpoints
    with algebra.JsonSchemaEntities
    with JsonSchemas
    with QueryStringLists
    with Validation {


  def queryEndpoint: Endpoint[((String, String, String), ApiQuery, String), Option[Either[List[QueryValidationError], List[Map[String, Option[Any]]]]]] =
    endpoint(
      request = post(url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / segment[String](name = "entity"),
        entity = jsonRequest[ApiQuery](),
        headers = header("apiKey")),
      response = validated(
        response = jsonResponse[List[Map[String, Option[Any]]]](docs = Some("Query endpoint")),
        invalidDocs = Some("Can't query - invalid entity!")
      ).orNotFound(Some("Not found"))
    )

  def blocksEndpoint =
    endpoint(
      request = get(
        url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / "blocks" /? myQueryStringParams,
        headers = header("apiKey")),
      response = jsonResponse[List[Map[String, Option[Any]]]](docs = Some("Query compatibility endpoint for blocks")).orNotFound(Some("Not found"))
    )

  implicit def qsInvFunctor: InvariantFunctor[QueryString]

  def blocksHeadEndpoint =
    endpoint(
      request = get(
        url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / "blocks" / "head",
        headers = header("apiKey")),
      response = jsonResponse[BlocksRow](docs = Some("Query compatibility endpoint for blocks head")).orNotFound(Some("Not found"))
    )

  def blockByHashEndpoint =
    endpoint(
      request = get(
        url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / "blocks" / segment[String](name = "hash") ,
        headers = header("apiKey")),
      response = jsonResponse[Map[String, Any]](docs = Some("Query compatibility endpoint for block")).orNotFound(Some("Not found"))
    )

  def accountsEndpoint =
    endpoint(
      request = get(
        url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / "accounts" /? myQueryStringParams,
        headers = header("apiKey")),
      response = jsonResponse[List[Map[String, Option[Any]]]](docs = Some("Query compatibility endpoint for accounts")).orNotFound(Some("Not found"))
    )

  def accountByIdEndpoint =
    endpoint(
      request = get(
        url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / "accounts" / segment[String](name = "accountId") ,
        headers = header("apiKey")),
      response = jsonResponse[Map[String, Any]](docs = Some("Query compatibility endpoint for account")).orNotFound(Some("Not found"))
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


  import shapeless._
  import ops.tuple.FlatMapper


  trait LowPriorityFlatten extends Poly1 {
    implicit def default[T] = at[T](Tuple1(_))
  }
  object flatten extends LowPriorityFlatten {
    implicit def caseTuple[P <: Product](implicit lfm: Lazy[FlatMapper[P, flatten.type]]) =
      at[P](lfm.value(_))
  }

  val myQueryStringParams = filterQs.xmap[Filter] (
    { filters =>
      val flattenedFilters = flatten(filters)
      val toFilter = (Filter.readParams _).tupled
      val q = toFilter(flattenedFilters)
      q
    }, {
      _: Filter => ???
    }
  )


}
