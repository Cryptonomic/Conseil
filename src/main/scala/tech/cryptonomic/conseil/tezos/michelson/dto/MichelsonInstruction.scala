package tech.cryptonomic.conseil.tezos.michelson.dto

/*
 * Class representing a Michelson instruction (code section)
 *
 * Example:
 *
 *   { DIP { DIP { DUP } ; NIL operation } ; SWAP ; {} }
 *   | |   | |     |       |                 |      |
 *   | |   | |     |       |                 |      MichelsonEmptyInstruction
 *   | |   | |     |       |                 MichelsonSingleInstruction (not typed)
 *   | |   | |     |       MichelsonSingleInstruction (with type "operation")
 *   | |   | |     MichelsonSingleInstruction (not typed)
 *   | |   | MichelsonSingleInstruction (with embedded "DUP" instruction as a one element List)
 *   | |   MichelsonInstructionSequence (containing a complex instruction "DIP { ... }" and a simple one "NIL operation" separated with ";")
 *   | MichelsonSingleInstruction (with embedded MichelsonInstructionSequence as above)
 *   MichelsonInstructionSequence (with three instructions separated with ";": "DIP { ... }", "SWAP" and empty instruction)
 * */
sealed trait MichelsonInstruction extends MichelsonElement

/* Class representing a simple Michelson instruction which can contains following expressions */
case class MichelsonSingleInstruction(
                                       name: String,
                                       embeddedElements: List[MichelsonElement] = List.empty,
                                       annotations: List[String] = List.empty) extends MichelsonInstruction

/* Class representing a sequence of Michelson instructions */
case class MichelsonInstructionSequence(instructions: List[MichelsonInstruction] = List.empty) extends MichelsonInstruction

/* Class representing an empty Michelson instruction */
case object MichelsonEmptyInstruction extends MichelsonInstruction