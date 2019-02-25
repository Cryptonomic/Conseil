package tech.cryptonomic.conseil.tezos.michelson

import tech.cryptonomic.conseil.tezos.michelson.dto.{MichelsonElement, MichelsonInstruction}
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser
import tech.cryptonomic.conseil.tezos.michelson.renderer.MichelsonRenderer._

/* Converts Michelson schema from JSON to its native format */
object JsonToMichelson {

  type Result[T] = Either[Throwable, T]

  def convert[T <: MichelsonElement](json: String): Result[String] = {
    JsonParser.parse[T](json).map(_.render())
  }
}
