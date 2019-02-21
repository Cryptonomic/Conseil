package tech.cryptonomic.conseil.tezos.michelson.renderer

import tech.cryptonomic.conseil.tezos.michelson.dto._

/* Implicitly adds render() methods for domain object representing Michelson Schema */
object MichelsonRenderer {

  implicit class MichelsonSchemaRenderer(val self: MichelsonSchema) {
    def render(): String = {
      val parameter = self.parameter.render()
      val storage = self.storage.render()
      val code = self.code.render()

      s"""parameter $parameter;
         |storage $storage;
         |code { $code }""".stripMargin
    }
  }

  implicit class MichelsonExpressionRenderer(val self: MichelsonExpression) {
    def render(): String = self match {
      case MichelsonType(name, List()) => name
      case MichelsonType(name, args) => s"($name ${args.map(_.render()).mkString(" ")})"
      case MichelsonIntConstant(constant) => constant.toString
      case MichelsonStringConstant(constant) => constant
    }
  }

  implicit class MichelsonCodeRenderer(val self: MichelsonCode) {
    def render(): String = self.instructions
      .map(_.render())
      .mkString(" ;\n       ")
  }

  implicit class MichelsonInstructionRenderer(val self: MichelsonInstruction) extends AnyVal {
    def render(): String = self match {
      case MichelsonSimpleInstruction(prim, List()) => prim
      case MichelsonSimpleInstruction(prim, args) => s"$prim ${args.map(_.render()).mkString(" ")}"
      case MichelsonComplexInstruction(prim, args) => s"$prim ${args.render()}"
      case MichelsonInstructionSequence(args) => s"{ ${args.map(_.render()).mkString(" ; ")} }"
    }
  }
}
