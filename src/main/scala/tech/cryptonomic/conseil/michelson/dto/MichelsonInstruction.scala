package tech.cryptonomic.conseil.michelson.dto

sealed trait MichelsonInstruction

case class MichelsonSimpleInstruction(prim: String, michelsonType: Option[MichelsonType] = None) extends MichelsonInstruction

case class MichelsonComplexInstruction(prim: String, embeddedOperations: MichelsonInstructionSequence) extends MichelsonInstruction

case class MichelsonInstructionSequence(instructions: Seq[MichelsonInstruction]) extends MichelsonInstruction