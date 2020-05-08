package tech.cryptonomic.conseil.common.tezos.michelson.dto

/*
 * Class representing a Michelson instruction (code section).
 *
 * In fact, you can use this type to represent Data as well. According to the grammar, it should be a separate type for
 * Data but since Data type is represented in the same way as an Instruction, the code is simplified and treats Data as
 * Instruction.
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
sealed trait MichelsonInstruction extends MichelsonElement {
  lazy val normalized: MichelsonInstruction = this
}

/* Class representing a simple Michelson instruction which can contains following expressions */
case class MichelsonSingleInstruction(
    name: String,
    embeddedElements: List[MichelsonElement] = List.empty,
    annotations: List[String] = List.empty
) extends MichelsonInstruction

/* Class representing a sequence of Michelson instructions */
case class MichelsonInstructionSequence(instructions: List[MichelsonInstruction] = List.empty)
    extends MichelsonInstruction {
  override lazy val normalized: MichelsonInstruction = if (instructions.isEmpty) MichelsonEmptyInstruction else this
}

/* Class representing an int constant */
case class MichelsonIntConstant(int: String) extends MichelsonInstruction

/* Class representing a string constant */
case class MichelsonStringConstant(string: String) extends MichelsonInstruction

/* Class representing a bytes constant */
case class MichelsonBytesConstant(bytes: String) extends MichelsonInstruction

/* Class representing an empty Michelson instruction */
case object MichelsonEmptyInstruction extends MichelsonInstruction
