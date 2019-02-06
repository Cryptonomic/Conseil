package tech.cryptonomic.conseil.michelson

import tech.cryptonomic.conseil.michelson.parser.JsonParser
import tech.cryptonomic.conseil.michelson.presenter.MichelsonPresenter._

class JsonToMichelson {

  case class ConversionError(throwable: Throwable) extends Exception

  private val parser = new JsonParser()

  def convert(json: String): Either[ConversionError, String] =
    parser.parse(json) match {
      case Right(jsonSchema) =>

        val parameter = jsonSchema.parameter.render()
        val storage = jsonSchema.storage.render()
        val code = jsonSchema.code.render()

        Right(s"""parameter $parameter;
                |storage $storage;
                |code { $code }""".stripMargin)
      case Left(error: Throwable) =>
        Left(ConversionError(error))
    }
}
