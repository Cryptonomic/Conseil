package tech.cryptonomic.conseil.api.info

import io.circe.generic.semiauto._

object converters {

  import tech.cryptonomic.conseil.api.info.model._

  implicit val gitInfoEncoder = deriveEncoder[GitInfo]
  implicit val gitInfoDecoder = deriveDecoder[GitInfo]

  implicit val infoEncoder = deriveEncoder[Info]
  implicit val infoDecoder = deriveDecoder[Info]

  implicit val genericServerErrorEncoder = deriveEncoder[GenericServerError]
  implicit val genericServerErrorDecoder = deriveDecoder[GenericServerError]

}
