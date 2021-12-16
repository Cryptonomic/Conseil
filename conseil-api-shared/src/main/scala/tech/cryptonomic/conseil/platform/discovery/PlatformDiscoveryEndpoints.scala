package tech.cryptonomic.conseil.platform.discovery

import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.model.StatusCode

import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes._

/** Trait containing platform discovery endpoints definition */
trait PlatformDiscoveryEndpoints {

  import tech.cryptonomic.conseil.platform.discovery.converters._

  val discoveryEndpoints = List(
    platformsEndpoint,
    networksEndpoint,
    entitiesEndpoint,
    attributesEndpoint,
    attributesValuesEndpoint,
    attributesValuesWithFilterEndpoint
  )

  /** Common path for metadata endpoints */
  private val commonPath = infallibleEndpoint
    .in("v2" / "metadata")
    .in(header[Option[String]]("apiKey"))

  /** Metadata platforms endpoint */
  def platformsEndpoint =
    commonPath.get
      .in("platforms")
      .out(jsonBody[List[Platform]])

  /** Metadata networks endpoint */
  def networksEndpoint =
    commonPath.get
      .in(query[String]("platform") / "networks")
      .out(jsonBody[List[Network]])
      .errorOut(statusCode(StatusCode.NotFound))

  /** Metadata entities endpoint */
  def entitiesEndpoint =
    commonPath.get
      .in(query[String]("platform") / query[String]("network") / "entities")
      .out(jsonBody[List[Entity]])
      .errorOut(statusCode(StatusCode.NotFound))

  /** Metadata attributes endpoint */
  def attributesEndpoint =
    commonPath.get
      .in(query[String]("platform") / query[String]("network") / query[String]("entity") / "attributes")
      .out(jsonBody[List[Attribute]])
      .errorOut(statusCode(StatusCode.NotFound))

  /** Metadata attributes values endpoint */
  def attributesValuesEndpoint =
    commonPath.get
      .in(query[String]("platform") / query[String]("network") / query[String]("entity") / query[String]("attribute"))
      .out(jsonBody[List[String]])
      .errorOut(statusCode(StatusCode.NotFound))

  /** Metadata attributes values with filter endpoint */
  def attributesValuesWithFilterEndpoint =
    commonPath.get
      .in(
        query[String]("platform")
          / query[String]("network")
          / query[String]("entity")
          / query[String]("attribute")
          / query[String]("filter")
      )
      .out(jsonBody[List[String]])

}
