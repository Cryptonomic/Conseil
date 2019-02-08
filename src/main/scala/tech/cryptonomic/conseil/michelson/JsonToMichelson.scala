package tech.cryptonomic.conseil.michelson

import tech.cryptonomic.conseil.michelson.parser.JsonParser
import tech.cryptonomic.conseil.michelson.presenter.MichelsonPresenter._

class JsonToMichelson {

  private val parser = new JsonParser()

  type Result[T] = Either[Throwable, T]

  def convert(json: String): Result[String] = parser
    .parse(json)
    .map(_.render())
}
