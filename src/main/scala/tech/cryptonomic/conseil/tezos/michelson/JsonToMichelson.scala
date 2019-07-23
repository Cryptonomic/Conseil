package tech.cryptonomic.conseil.tezos.michelson

import tech.cryptonomic.conseil.tezos.michelson.dto.MichelsonElement
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser.Parser
import tech.cryptonomic.conseil.tezos.michelson.renderer.MichelsonRenderer._
import tech.cryptonomic.conseil.util.Conversion

/* Converts Michelson schema from JSON to its native format */
object JsonToMichelson {

  type Result[T] = Either[Throwable, T]

  def convert[T <: MichelsonElement: Parser](json: String): Result[String] =
    JsonParser.parse[T](json).map(_.render())

  implicit def michelsonConversions[T] = new Conversion[Either[Throwable, ?], String, String] {
    override def convert(from: String): Either[Throwable, String] = convert(from)
  }

}
