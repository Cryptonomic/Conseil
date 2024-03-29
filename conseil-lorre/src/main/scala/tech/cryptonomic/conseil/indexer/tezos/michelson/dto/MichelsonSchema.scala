package tech.cryptonomic.conseil.indexer.tezos.michelson.dto

/* Class representing a whole Michelson schema */
case class MichelsonSchema(
    parameter: MichelsonExpression,
    storage: MichelsonExpression,
    code: MichelsonCode,
    view: List[MichelsonInstruction] = List.empty
) extends MichelsonElement

object MichelsonSchema {
  lazy val empty: MichelsonSchema =
    MichelsonSchema(MichelsonEmptyExpression, MichelsonEmptyExpression, MichelsonCode(List.empty), List.empty)
}
