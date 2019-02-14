package tech.cryptonomic.conseil.tezos.michelson.dto

/*
 * Class representing a Michelson instruction (code section)
 *
 * Example:
 *
 *   { DIP { DIP { DUP } ; NIL operation } ; SWAP }
 *   | |   | |     |       |                 |
 *   | |   | |     |       |                 MichelsonSimpleInstruction (not typed)
 *   | |   | |     |       MichelsonSimpleInstruction (with type "operation")
 *   | |   | |     MichelsonSimpleInstruction (not typed)
 *   | |   | MichelsonComplexInstruction (with embedded "DUP" instruction as a one element List)
 *   | |   MichelsonInstructionSequence (containing a complex instruction "DIP { ... }" and a simple one "NIL operation" separated with ";")
 *   | MichelsonComplexInstruction (with embedded MichelsonInstructionSequence as above)
 *   MichelsonInstructionSequence (with two instructions separated with ";": "DIP { ... }" and "SWAP")
 * */
sealed trait MichelsonInstruction

/* Class representing a simple Michelson instruction which can by optionally typed */
case class MichelsonSimpleInstruction(prim: String, michelsonType: Option[MichelsonType] = None) extends MichelsonInstruction

/* Class representing a Michelson instruction with other embedded instructions */
case class MichelsonComplexInstruction(prim: String, embeddedOperations: MichelsonInstructionSequence) extends MichelsonInstruction

/* Class representing a sequence of Michelson instructions */
case class MichelsonInstructionSequence(instructions: List[MichelsonInstruction]) extends MichelsonInstruction
