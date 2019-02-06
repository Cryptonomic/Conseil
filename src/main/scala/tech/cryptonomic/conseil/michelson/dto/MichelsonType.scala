package tech.cryptonomic.conseil.michelson.dto

case class MichelsonType(prim: String, args: Seq[MichelsonType] = Seq())
