package tech.cryptonomic.conseil.platform.data

import io.circe.{Decoder, Encoder}
import sttp.tapir._
import sttp.tapir.json.circe._

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponseWithOutput
import sttp.model.StatusCode

private[data] trait ApiDataEndpoints {

  import tech.cryptonomic.conseil.platform.data.converters._
  import tech.cryptonomic.conseil.platform.data.schemas._

  protected def commonPath(platform: String): Endpoint[Unit, Unit, Nothing, Unit, Any] =
    infallibleEndpoint.in("v2" / "data" / platform / "network" / "entity")

  /** V2 Query endpoint definition */
  def queryEndpoint(platform: String) =
    commonPath(platform).post
      .out(jsonBody[QueryResponseWithOutput])

  /** Common method for compatibility queries */
  def compatibilityQuery[A: Encoder: Decoder: Schema](endpointName: String) =
    jsonBody[A]
      .and(statusCode(StatusCode.Ok))

}
