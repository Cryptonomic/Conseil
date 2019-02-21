package tech.cryptonomic.conseil.tezos.michelson

import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser.parse
import tech.cryptonomic.conseil.tezos.michelson.renderer.MichelsonRenderer._

/* Converts Michelson schema from JSON to its native format */
object JsonToMichelson {

  type Result[T] = Either[Throwable, T]

  /* Converts Michelson schema from JSON to its native format */
  def convert(json: String): Result[String] = parse(json).map(_.render())
}
