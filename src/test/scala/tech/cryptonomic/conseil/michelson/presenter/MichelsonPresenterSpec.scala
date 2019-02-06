package tech.cryptonomic.conseil.michelson.presenter

import org.scalatest._
import tech.cryptonomic.conseil.michelson.dto._
import tech.cryptonomic.conseil.michelson.presenter.MichelsonPresenter._

class MichelsonPresenterSpec extends FlatSpec with Matchers {

  "A MichelsonPresenter" should "render MichelsonType" in {
    val michelsonType = MichelsonType("contract", Seq(MichelsonType("or",
      Seq(
        MichelsonType("option", Seq(MichelsonType("address", Seq()))),
        MichelsonType("int", Seq())
      )
    )))

    michelsonType.render() shouldBe "(contract (or (option address) int))"
  }

  "A MichelsonPresenter" should "render MichelsonExpression" in {
    val michelsonExpression = MichelsonCode(Seq(
      MichelsonSimpleInstruction("CDR"),
      MichelsonSimpleInstruction("DUP"),
      MichelsonSimpleInstruction("NIL", Some(MichelsonType("operation"))),
      MichelsonInstructionSequence(Seq(
        MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(Seq(
          MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(Seq(
            MichelsonSimpleInstruction("DUP")))),
          MichelsonSimpleInstruction("SWAP")))),
        MichelsonSimpleInstruction("SWAP"))),
      MichelsonInstructionSequence(Seq(
        MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(Seq(
          MichelsonComplexInstruction("DIP",
            MichelsonInstructionSequence(Seq(MichelsonSimpleInstruction("DUP")))),
          MichelsonSimpleInstruction("NIL", Some(MichelsonType("operation")))))),
        MichelsonSimpleInstruction("SWAP")))
    ))

    michelsonExpression.render() shouldBe """CDR ;
                                            |       DUP ;
                                            |       NIL operation ;
                                            |       { DIP { DIP { DUP } ; SWAP } ; SWAP } ;
                                            |       { DIP { DIP { DUP } ; NIL operation } ; SWAP }""".stripMargin
  }
}
