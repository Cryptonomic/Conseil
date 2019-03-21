package tech.cryptonomic.conseil.tezos.michelson.renderer

import tech.cryptonomic.conseil.tezos.michelson.dto._

/* Implicitly adds render() methods for domain object representing Michelson Schema */
object MichelsonRenderer {

  implicit class MichelsonElementRenderer(val self: MichelsonElement) {
    def render(): String = self match {

      // instructions
      case MichelsonSingleInstruction(prim, List(), List()) => prim
      case MichelsonSingleInstruction(prim, args, annotations) => s"$prim ${(annotations ++ args.map(_.render())).mkString(" ")}"
      case MichelsonInstructionSequence(args) => s"{ ${args.map(_.render()).mkString(" ; ")} }"
      case MichelsonEmptyInstruction => "{}"

      // expressions
      case MichelsonType(name, List(), List()) => name
      case MichelsonType(name, args, annotations) => s"($name ${(annotations ++ args.map(_.render())).mkString(" ")})"
      case MichelsonIntConstant(constant) => constant.toString
      case MichelsonStringConstant(constant) => "\"%s\"".format(constant)
      case MichelsonEmptyExpression => "{}"

      // code
      case MichelsonCode(instructions) => instructions.map(_.render()).mkString(" ;\n       ")

      // schema
      case MichelsonSchema(MichelsonEmptyExpression, MichelsonEmptyExpression, MichelsonCode(Nil)) => ""
      case MichelsonSchema(parameter, storage, code) => s"""parameter ${parameter.render()};
                                                           |storage ${storage.render()};
                                                           |code { ${code.render()} }""".stripMargin
    }
  }
}
