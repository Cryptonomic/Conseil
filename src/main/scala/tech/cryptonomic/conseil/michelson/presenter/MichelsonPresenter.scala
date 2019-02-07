package tech.cryptonomic.conseil.michelson.presenter

import tech.cryptonomic.conseil.michelson.dto._

object MichelsonPresenter {

  implicit class MichelsonSchemaPresenter(val self: MichelsonSchema) extends AnyVal {
    def render(): String = {
      val parameter = self.parameter.render()
      val storage = self.storage.render()
      val code = self.code.render()

      s"""parameter $parameter;
         |storage $storage;
         |code { $code }""".stripMargin
    }
  }

  implicit class MichelsonTypePresenter(val self: MichelsonType) extends AnyVal {
    def render(): String = self match {
      case MichelsonType(name, Seq()) => name
      case MichelsonType(name, Seq(arg)) => s"($name ${arg.render()})"
      case MichelsonType(name, Seq(arg1, arg2)) => s"($name ${arg1.render()} ${arg2.render()})"
    }
  }

  implicit class MichelsonCodePresenter(val self: MichelsonCode) extends AnyVal {
    def render(): String = self.instructions
      .map(_.render())
      .mkString(" ;\n       ")
  }

  implicit class MichelsonInstructionPresenter(val self: MichelsonInstruction) extends AnyVal {
    def render(): String = self match {
      case MichelsonSimpleInstruction(prim, None) => prim
      case MichelsonSimpleInstruction(prim, Some(michelsonType)) => s"$prim ${michelsonType.render()}"
      case MichelsonComplexInstruction(prim, args) => s"$prim ${args.render()}"
      case MichelsonInstructionSequence(args) => s"{ ${args.map(_.render()).mkString(" ; ")} }"
    }
  }
}
