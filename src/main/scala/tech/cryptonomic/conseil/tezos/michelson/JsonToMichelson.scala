package tech.cryptonomic.conseil.tezos.michelson

import tech.cryptonomic.conseil.tezos.michelson.dto.{MichelsonCode, MichelsonExpression, MichelsonInstruction, MichelsonSchema}
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser
import tech.cryptonomic.conseil.tezos.michelson.renderer.MichelsonRenderer._

/* Converts Michelson schema from JSON to its native format */
object JsonToMichelson {

  type Result[T] = Either[Throwable, T]

  def convertCode(json: String): Result[String] = {
    JsonParser.parse[MichelsonCode](json).map(_.render())
  }

  def convertSchema(json: String): Result[String] = {
    JsonParser.parse[MichelsonSchema](json).map(_.render())
  }

  def convertExpression(json: String): Result[String] = {
    JsonParser.parse[MichelsonExpression](json).map(_.render())
  }

  def convertInstruction(json: String): Result[String] = {
    JsonParser.parse[MichelsonInstruction](json).map(_.render())
  }
}
