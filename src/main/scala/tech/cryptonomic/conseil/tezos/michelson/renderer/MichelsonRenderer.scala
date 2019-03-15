package tech.cryptonomic.conseil.tezos.michelson.renderer

import tech.cryptonomic.conseil.tezos.michelson.dto._

/* Implicitly adds render() methods for domain object representing Michelson Schema */
object MichelsonRenderer {

  implicit class MichelsonElementRenderer(val self: MichelsonElement) {
    def render(): String = self match {

      // instructions
      case MichelsonSimpleInstruction(prim, List()) => prim
      case MichelsonSimpleInstruction(prim, args) => s"$prim ${args.map(_.render()).mkString(" ")}"
      case MichelsonInstructionSequence(args) => s"{ ${args.map(_.render()).mkString(" ; ")} }"
      case MichelsonEmptyInstruction => "{}"

      // expressions
      case MichelsonType(name, List()) => name
      case MichelsonType(name, args) => s"($name ${args.map(_.render()).mkString(" ")})"
      case MichelsonIntConstant(constant) => constant.toString
      case MichelsonStringConstant(constant) => "\"%s\"".format(constant)
      case MichelsonEmptyExpression => "{}"

      case MichelsonCode(instructions) => instructions.map(_.render()).mkString(" ;\n       ")

      case MichelsonSchema(parameter, storage, code) => s"""parameter ${parameter.render()};
                                                           |storage ${storage.render()};
                                                           |code { ${code.render()} }""".stripMargin
    }
  }
}
