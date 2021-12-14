package tech.cryptonomic.conseil

import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.generic.auto._

object protocol {

  import tech.cryptonomic.conseil.info.model._
  import tech.cryptonomic.conseil.info.converters._

  private val base = infallibleEndpoint.in("api")

  val appInfo: Endpoint[Unit, String, GenericServerError, Info, Any] = base.get
    .in("info")
    .in(header[String]("apiKey"))
    .out(jsonBody[Info])
    .errorOut(jsonBody[GenericServerError])

}
