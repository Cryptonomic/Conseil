package tech.cryptonomic.conseil.indexer.tezos.michelson.dto

import scala.util.Try

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

  def findInstruction(
      pattern: MichelsonElement,
      in: MichelsonElement = this,
      acc: List[String] = List.empty
  ): List[String] =
    if (pattern == in) {
      List(acc.reverse.mkString(";"))
    } else {
      in match {
        case MichelsonCode(instructions) =>
          instructions.zipWithIndex.flatMap {
            case (instruction, index) =>
              findInstruction(pattern, instruction, s"MichelsonCode:$index" :: acc)
          }
        case expression: MichelsonExpression =>
          expression match {
            case MichelsonType(prim, args, annotations) =>
              args.zipWithIndex.flatMap {
                case (instruction, index) =>
                  findInstruction(
                    pattern,
                    instruction,
                    s"MichelsonType:$prim:${annotations.mkString("&")}:$index" :: acc
                  )
              }
            case MichelsonEmptyExpression => List.empty
          }
        case instruction: MichelsonInstruction =>
          instruction match {
            case MichelsonSingleInstruction(name, embeddedElements, annotations) =>
              embeddedElements.zipWithIndex.flatMap {
                case (instruction, index) =>
                  findInstruction(
                    pattern,
                    instruction,
                    s"MichelsonSingleInstruction:$name:${annotations.mkString("&")}:$index" :: acc
                  )
              }
            case MichelsonInstructionSequence(instructions) =>
              instructions.zipWithIndex.flatMap {
                case (instruction, index) =>
                  findInstruction(pattern, instruction, s"MichelsonInstructionSequence:$index" :: acc)
              }
            case _ => List.empty
          }
        case MichelsonSchema(_, _, _) => List.empty
        case _ => List.empty
      }
    }

  def getAtPath(path: String): Option[MichelsonElement] = {
    val pathList = path.split(';').toList
    val msiPattern = "(MichelsonSingleInstruction|MichelsonType):([0-9a-zA-Z]+):(\\d{0}|[0-9a-zA-Z]+):(\\d)".r
    val misPattern = "(MichelsonInstructionSequence|MichelsonCode):(\\d+)".r

    def getAtPathHelper(p: List[String], in: MichelsonElement): Option[MichelsonElement] =
      p match {
        case Nil =>
          Some(in)
        case head :: tail =>
          head match {
            case msiPattern(_, name, annots, index) =>
              in match {
                case instruction: MichelsonInstruction =>
                  instruction match {
                    case MichelsonSingleInstruction(n, e, a)
                        if name == n && annots.split('&').toList.filter(_.nonEmpty) == a =>
                      Try(getAtPathHelper(tail, e(index.toInt))).toOption.flatten
                    case _ => None
                  }
                case instruction: MichelsonType =>
                  instruction match {
                    case MichelsonType(n, e, a) if name == n && annots.split('&').toList.filter(_.nonEmpty) == a =>
                      Try(getAtPathHelper(tail, e(index.toInt))).toOption.flatten
                    case _ => None
                  }
                case _ => None
              }

            case misPattern(_, index) =>
              in match {
                case instruction: MichelsonInstruction =>
                  instruction match {
                    case MichelsonInstructionSequence(instructions) =>
                      Try(getAtPathHelper(tail, instructions(index.toInt))).toOption.flatten
                    case _ => None
                  }
                case MichelsonCode(instructions) =>
                  Try(getAtPathHelper(tail, instructions(index.toInt))).toOption.flatten
                case _ => None
              }
          }
      }
    getAtPathHelper(pathList, this)
  }

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
