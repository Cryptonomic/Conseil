package tech.cryptonomic.conseil.common.tezos.michelson.dto

/* Class representing a code section of Michelson schema */
case class MichelsonCode(instructions: List[MichelsonInstruction] = List.empty) extends MichelsonElement
