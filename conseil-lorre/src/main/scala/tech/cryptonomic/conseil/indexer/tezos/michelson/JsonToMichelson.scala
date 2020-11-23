package tech.cryptonomic.conseil.indexer.tezos.michelson

import tech.cryptonomic.conseil.indexer.tezos.michelson.dto.MichelsonElement
import tech.cryptonomic.conseil.indexer.tezos.michelson.parser.JsonParser
import tech.cryptonomic.conseil.indexer.tezos.michelson.parser.JsonParser.Parser
import tech.cryptonomic.conseil.indexer.tezos.michelson.renderer.MichelsonRenderer._
import scala.reflect.ClassTag
import scala.util.Try
import scribe._

/* Converts Michelson schema from JSON to its native format */
object JsonToMichelson {

  type Result[T] = Either[Throwable, T]

  def convert[T <: MichelsonElement: Parser](json: String): Result[String] =
    JsonParser.parse[T](json).map(_.render())

  def toMichelsonScript[T <: MichelsonElement: Parser](
      json: String
  )(implicit tag: ClassTag[T], logger: Logger): String = {

    def unparsableResult(json: Any, exception: Option[Throwable] = None): String = {
      exception match {
        case Some(t) => logger.error(s"${tag.runtimeClass}: Error during conversion of $json", t)
        case None => logger.error(s"${tag.runtimeClass}: Error during conversion of $json")
      }

      s"Unparsable code: $json"
    }

    def parse(json: String): String = convert[T](json) match {
      case Right(convertedResult) => convertedResult
      case Left(exception) => unparsableResult(json, Some(exception))
    }

    Try(parse(json)).getOrElse(unparsableResult(json))
  }

}
