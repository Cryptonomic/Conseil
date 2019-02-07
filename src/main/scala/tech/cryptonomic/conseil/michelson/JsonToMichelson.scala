package tech.cryptonomic.conseil.michelson

import tech.cryptonomic.conseil.michelson.parser.JsonParser
import tech.cryptonomic.conseil.michelson.presenter.MichelsonPresenter._

class JsonToMichelson {

  case class ConversionError(throwable: Throwable) extends Exception
  private val parser = new JsonParser()

  type Result = Either[Throwable, String]

  def convert(json: String): Result = parser
    .parse(json)
    .map(_.render())
}
