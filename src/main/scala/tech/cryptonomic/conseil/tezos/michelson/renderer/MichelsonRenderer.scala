package tech.cryptonomic.conseil.tezos.michelson.renderer

import tech.cryptonomic.conseil.tezos.michelson.dto._

/* Implicitly adds render() methods for domain object representing Michelson Schema */
object MichelsonRenderer {

  implicit class MichelsonElementRenderer(val self: MichelsonElement) {
    def render(): String = self match {

      // instructions
      case MichelsonSingleInstruction(
          name,
          List(sequence1: MichelsonInstructionSequence, sequence2: MichelsonInstructionSequence),
          _
          ) => {
        val indent = " " * (name.length + 1)
        val embeddedIndent = indent + " " * 2

        s"""$name { ${sequence1.instructions.render(embeddedIndent)} }
           |$indent{ ${sequence2.instructions.render(embeddedIndent)} }""".stripMargin
      }
      case MichelsonSingleInstruction(name, Nil, Nil) => name
      case MichelsonSingleInstruction(name, args, annotations) =>
        s"$name ${(annotations ++ args.map(_.render())).mkString(" ")}"
      case MichelsonInstructionSequence(args) => s"{ ${args.map(_.render()).mkString(" ; ")} }"
      case MichelsonEmptyInstruction => "{}"

      // expressions
      case MichelsonType(name, Nil, Nil) => name
      case MichelsonType(name, args, annotations) => s"($name ${(annotations ++ args.map(_.render())).mkString(" ")})"
      case MichelsonIntConstant(constant) => constant.toString
      case MichelsonStringConstant(constant) => "\"%s\"".format(constant)
      case MichelsonBytesConstant(constant) => s"0x$constant"
      case MichelsonEmptyExpression => "{}"

      // code
      case MichelsonCode(instructions) => instructions.render(indent = 7)

      // schema
      case MichelsonSchema(MichelsonEmptyExpression, MichelsonEmptyExpression, MichelsonCode(Nil)) => ""
      case MichelsonSchema(parameter, storage, code) => s"""parameter ${parameter.render()};
                                                           |storage ${storage.render()};
                                                           |code { ${code.render()} }""".stripMargin
    }
  }

  implicit private class MichelsonInstructionsRenderer(val self: List[MichelsonInstruction]) {
    def render(indent: Int): String = self.render(" " * indent)

    def render(indent: String): String =
      self
        .map(_.render())
        .mkString(" ;\n")
        .lines
        .mkString("\n" + indent)
  }
}
