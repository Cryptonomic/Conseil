package tech.cryptonomic.conseil.michelson.presenter

import org.scalatest._
import tech.cryptonomic.conseil.michelson.dto._
import tech.cryptonomic.conseil.michelson.presenter.MichelsonPresenter._

class MichelsonPresenterSpec extends FlatSpec with Matchers {

  it should "render single MichelsonType" in {
    MichelsonType("contract").render() shouldBe "contract"
  }

  it should "render one-argument MichelsonType" in {
    MichelsonType("option", Seq(MichelsonType("address"))).render() shouldBe "(option address)"
  }

  it should "render two-argument MichelsonType" in {
    MichelsonType("pair", Seq(MichelsonType("int"), MichelsonType("address"))).render() shouldBe "(pair int address)"
  }

  it should "render complex MichelsonType" in {
    val michelsonType = MichelsonType("contract", Seq(MichelsonType("or", Seq(
        MichelsonType("option", Seq(
          MichelsonType("address"))),
        MichelsonType("int")))))

    michelsonType.render() shouldBe "(contract (or (option address) int))"
  }

  it should "render MichelsonCode with only one simple instruction" in {
    val michelsonCode = MichelsonCode(Seq(MichelsonSimpleInstruction("CDR")))

    michelsonCode.render() shouldBe "CDR"
  }

  it should "render MichelsonCode with two simple instructions" in {
    val michelsonCode = MichelsonCode(Seq(MichelsonSimpleInstruction("CDR"), MichelsonSimpleInstruction("DUP")))

    michelsonCode.render() shouldBe """CDR ;
                                            |       DUP""".stripMargin
  }

  it should "render MichelsonCode with typed instruction" in {
    val michelsonCode = MichelsonCode(Seq(MichelsonSimpleInstruction("NIL", Some(MichelsonType("operation")))))

    michelsonCode.render() shouldBe "NIL operation"
  }

  it should "render MichelsonCode with instruction sequence" in {
    val michelsonCode = MichelsonCode(Seq(
      MichelsonInstructionSequence(Seq(
        MichelsonSimpleInstruction("DIP"),
        MichelsonSimpleInstruction("SWAP")))))

    michelsonCode.render() shouldBe "{ DIP ; SWAP }"
  }

  it should "render MichelsonCode with complex instruction" in {
    val michelsonCode = MichelsonCode(Seq(
      MichelsonInstructionSequence(Seq(
        MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(Seq(
          MichelsonSimpleInstruction("DUP"))))))))

    michelsonCode.render() shouldBe "{ DIP { DUP } }"
  }

  it should "render complex MichelsonCode" in {
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
          MichelsonSimpleInstruction("NIL", Some(
            MichelsonType("operation")))))),
        MichelsonSimpleInstruction("SWAP")))
    ))

    michelsonExpression.render() shouldBe """CDR ;
                                            |       DUP ;
                                            |       NIL operation ;
                                            |       { DIP { DIP { DUP } ; SWAP } ; SWAP } ;
                                            |       { DIP { DIP { DUP } ; NIL operation } ; SWAP }""".stripMargin
  }
}
