package tech.cryptonomic.conseil.michelson.presenter

import tech.cryptonomic.conseil.michelson.dto._

object MichelsonPresenter {

  implicit class MichelsonTypePresenter(val self: MichelsonType) extends AnyVal {
    def render(): String = {
      renderInternal(self)
    }

    private def renderInternal(michelsonType: MichelsonType): String = michelsonType match {
      case MichelsonType(name, Seq()) => name
      case MichelsonType(name, Seq(arg)) => s"($name ${renderInternal(arg)})"
      case MichelsonType(name, Seq(arg1, arg2)) => s"($name ${renderInternal(arg1)} ${renderInternal(arg2)})"
    }
  }

  implicit class MichelsonExpressionPresenter(val self: MichelsonCode) extends AnyVal {
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
