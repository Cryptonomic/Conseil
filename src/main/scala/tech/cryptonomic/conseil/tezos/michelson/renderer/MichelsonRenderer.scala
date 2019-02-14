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

  implicit class MichelsonTypeRenderer(val self: MichelsonType) {
    def render(): String = self match {
      case MichelsonType(name, List()) => name
      case MichelsonType(name, args) => s"($name ${args.map(_.render()).mkString(" ")})"
    }
  }

  implicit class MichelsonCodeRenderer(val self: MichelsonCode) {
    def render(): String = self.instructions
      .map(_.render())
      .mkString(" ;\n       ")
  }

  implicit class MichelsonInstructionRenderer(val self: MichelsonInstruction) extends AnyVal {
    def render(): String = self match {
      case MichelsonSimpleInstruction(prim, None) => prim
      case MichelsonSimpleInstruction(prim, Some(michelsonType)) => s"$prim ${michelsonType.render()}"
      case MichelsonComplexInstruction(prim, args) => s"$prim ${args.render()}"
      case MichelsonInstructionSequence(args) => s"{ ${args.map(_.render()).mkString(" ; ")} }"
    }
  }
}
