package tech.cryptonomic.conseil.platform.data

import io.circe.{Decoder, Encoder}
import sttp.tapir._
import sttp.tapir.json.circe._

trait ApiDataEndpoints {

  protected def commonPath(platform: String) =
    infallibleEndpoint.in("v2" / "data" / platform / "network" / "entity")

  /** V2 Query endpoint definition */
  def queryEndpoint(platform: String) =
    commonPath(platform).post
  // .out(jsonBodyQueryResponseWithOutput)

  /** Common method for compatibility queries */
  def compatibilityQuery[A: Encoder: Decoder: Schema](endpointName: String) = jsonBody[A]

}
