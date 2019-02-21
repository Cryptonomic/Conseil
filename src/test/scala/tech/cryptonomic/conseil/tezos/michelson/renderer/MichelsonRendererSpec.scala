package tech.cryptonomic.conseil.tezos.michelson.renderer

import org.scalatest._
import tech.cryptonomic.conseil.tezos.michelson.dto._
import tech.cryptonomic.conseil.tezos.michelson.renderer.MichelsonRenderer._

class MichelsonRendererSpec extends FlatSpec with Matchers {

  it should "render single MichelsonType" in {
    MichelsonType("contract").render() shouldBe "contract"
  }

  it should "render one-argument MichelsonType" in {
    MichelsonType("option", List(MichelsonType("address"))).render() shouldBe "(option address)"
  }

  it should "render two-argument MichelsonType" in {
    MichelsonType("pair", List(MichelsonType("int"), MichelsonType("address"))).render() shouldBe "(pair int address)"
  }

  it should "render MichelsonType with int constant" in {
    MichelsonType("some", List(MichelsonIntConstant(12))).render() shouldBe "(some 12)"
  }

  it should "render MichelsonType with string constant" in {
    MichelsonType("some", List(MichelsonStringConstant("testValue"))).render() shouldBe "(some testValue)"
  }

  it should "render complex MichelsonType" in {
    val michelsonType = MichelsonType("contract", List(MichelsonType("or", List(
      MichelsonType("option", List(
        MichelsonType("address"))),
      MichelsonType("int")))))

    michelsonType.render() shouldBe "(contract (or (option address) int))"
  }

  it should "render MichelsonCode with only one simple instruction" in {
    val michelsonCode = MichelsonCode(List(MichelsonSimpleInstruction("CDR")))

    michelsonCode.render() shouldBe "CDR"
  }

  it should "render MichelsonCode with two simple instructions" in {
    val michelsonCode = MichelsonCode(List(MichelsonSimpleInstruction("CDR"), MichelsonSimpleInstruction("DUP")))

    michelsonCode.render() shouldBe """CDR ;
                                      |       DUP""".stripMargin
  }

  it should "render MichelsonCode with typed instruction" in {
    val michelsonCode = MichelsonCode(List(MichelsonSimpleInstruction("NIL", List(MichelsonType("operation")))))

    michelsonCode.render() shouldBe "NIL operation"
  }

  it should "render MichelsonCode with typed instruction with constant" in {
    val michelsonCode = MichelsonCode(List(MichelsonSimpleInstruction("PUSH", List(MichelsonType("mutez"), MichelsonIntConstant(0)))))

    michelsonCode.render() shouldBe "PUSH mutez 0"
  }

  it should "render MichelsonCode with instruction sequence" in {
    val michelsonCode = MichelsonCode(List(
      MichelsonInstructionSequence(List(
        MichelsonSimpleInstruction("DIP"),
        MichelsonSimpleInstruction("SWAP")))))

    michelsonCode.render() shouldBe "{ DIP ; SWAP }"
  }

  it should "render MichelsonCode with complex instruction" in {
    val michelsonCode = MichelsonCode(List(
      MichelsonInstructionSequence(List(
        MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(List(
          MichelsonSimpleInstruction("DUP"))))))))

    michelsonCode.render() shouldBe "{ DIP { DUP } }"
  }

  it should "render complex MichelsonCode" in {
    val michelsonExpression = MichelsonCode(List(
      MichelsonSimpleInstruction("CDR"),
      MichelsonSimpleInstruction("DUP"),
      MichelsonSimpleInstruction("NIL", List(
        MichelsonType("operation"))),
      MichelsonInstructionSequence(List(
        MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(List(
          MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(List(
            MichelsonSimpleInstruction("DUP")))),
          MichelsonSimpleInstruction("SWAP")))),
        MichelsonSimpleInstruction("SWAP"))),
      MichelsonInstructionSequence(List(
        MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(List(
          MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(List(
            MichelsonSimpleInstruction("DUP")))),
          MichelsonSimpleInstruction("NIL", List(
            MichelsonType("operation")))))),
        MichelsonSimpleInstruction("SWAP")))
    ))

    michelsonExpression.render() shouldBe
      """CDR ;
        |       DUP ;
        |       NIL operation ;
        |       { DIP { DIP { DUP } ; SWAP } ; SWAP } ;
        |       { DIP { DIP { DUP } ; NIL operation } ; SWAP }""".stripMargin
  }
}
