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
      MichelsonType("some", List(MichelsonStringConstant("testValue"))).render() shouldBe "(some \"testValue\")"
    }

  it should "render MichelsonType with string constant including JSON" in {
      MichelsonType("some", List(MichelsonStringConstant("""{"key": "value"}""")))
        .render() shouldBe """(some "{\"key\": \"value\"}")"""
    }

  it should "render MichelsonType with bytes constant" in {
      MichelsonType("some", List(MichelsonBytesConstant("0500"))).render() shouldBe "(some 0x0500)"
    }

  it should "render MichelsonType with annotation" in {
      val michelsonType = MichelsonType(
        prim = "pair",
        args = List(MichelsonType("int", annotations = List("%x")), MichelsonType("int", annotations = List("%y"))),
        annotations = List(":point")
      )

      michelsonType.render() shouldBe "(pair :point (int %x) (int %y))"
    }

  it should "render complex MichelsonType" in {
      val michelsonType = MichelsonType(
        "contract",
        List(MichelsonType("or", List(MichelsonType("option", List(MichelsonType("address"))), MichelsonType("int"))))
      )

      michelsonType.render() shouldBe "(contract (or (option address) int))"
    }

  it should "render MichelsonCode with only one simple instruction" in {
      val michelsonCode = MichelsonCode(List(MichelsonSingleInstruction("CDR")))

      michelsonCode.render() shouldBe "CDR"
    }

  it should "render MichelsonCode with two simple instructions" in {
      val michelsonCode = MichelsonCode(List(MichelsonSingleInstruction("CDR"), MichelsonSingleInstruction("DUP")))

      michelsonCode.render() shouldBe """CDR ;
                                      |       DUP""".stripMargin
    }

  it should "render MichelsonCode with typed instruction" in {
      val michelsonCode = MichelsonCode(List(MichelsonSingleInstruction("NIL", List(MichelsonType("operation")))))

      michelsonCode.render() shouldBe "NIL operation"
    }

  it should "render MichelsonCode with typed instruction with constant" in {
      val michelsonCode =
        MichelsonCode(List(MichelsonSingleInstruction("PUSH", List(MichelsonType("mutez"), MichelsonIntConstant(0)))))

      michelsonCode.render() shouldBe "PUSH mutez 0"
    }

  it should "render MichelsonCode with instruction sequence" in {
      val michelsonCode = MichelsonCode(
        List(MichelsonInstructionSequence(List(MichelsonSingleInstruction("DIP"), MichelsonSingleInstruction("SWAP"))))
      )

      michelsonCode.render() shouldBe "{ DIP ; SWAP }"
    }

  it should "render MichelsonCode with complex instruction" in {
      val michelsonCode = MichelsonCode(
        List(
          MichelsonInstructionSequence(
            List(
              MichelsonSingleInstruction(
                "DIP",
                List(MichelsonInstructionSequence(List(MichelsonSingleInstruction("DUP"))))
              )
            )
          )
        )
      )

      michelsonCode.render() shouldBe "{ DIP { DUP } }"
    }

  it should "render MichelsonInstruction with empty embedded instruction" in {
      val michelsonInstruction = MichelsonInstructionSequence(
        List(
          MichelsonSingleInstruction(
            "IF_NONE",
            List(
              MichelsonInstructionSequence(
                List(
                  MichelsonInstructionSequence(
                    List(MichelsonSingleInstruction("UNIT"), MichelsonSingleInstruction("FAILWITH"))
                  )
                )
              ),
              MichelsonEmptyInstruction
            )
          )
        )
      )

      michelsonInstruction.render() shouldBe "{ IF_NONE { { UNIT ; FAILWITH } } {} }"
    }

  it should "render MichelsonInstruction IF" in {
      val michelsonInstruction =
        MichelsonSingleInstruction(
          "IF",
          List(
            MichelsonInstructionSequence(
              List(
                MichelsonSingleInstruction(
                  "PUSH",
                  List(MichelsonType("string"), MichelsonStringConstant("The image bid contract is now closed"))
                ),
                MichelsonSingleInstruction("FAILWITH")
              )
            ),
            MichelsonInstructionSequence(List(MichelsonSingleInstruction("UNIT")))
          )
        )

      michelsonInstruction.render() shouldBe
        """IF { PUSH string "The image bid contract is now closed" ;
        |     FAILWITH }
        |   { UNIT }""".stripMargin
    }

  it should "render MichelsonInstruction with embedded IF-s" in {
      val michelsonInstruction =
        MichelsonSingleInstruction(
          "IF",
          List(
            MichelsonInstructionSequence(
              List(
                MichelsonSingleInstruction(
                  "PUSH",
                  List(MichelsonType("string"), MichelsonStringConstant("The image bid contract is now closed"))
                ),
                MichelsonSingleInstruction("FAILWITH")
              )
            ),
            MichelsonInstructionSequence(
              List(
                MichelsonSingleInstruction(
                  "IF",
                  List(
                    MichelsonInstructionSequence(
                      List(
                        MichelsonSingleInstruction(
                          "PUSH",
                          List(MichelsonType("string"), MichelsonStringConstant("The image bid contract is now closed"))
                        ),
                        MichelsonSingleInstruction("FAILWITH")
                      )
                    ),
                    MichelsonInstructionSequence(List(MichelsonSingleInstruction("UNIT")))
                  )
                )
              )
            )
          )
        )

      michelsonInstruction.render() shouldBe
        """IF { PUSH string "The image bid contract is now closed" ;
        |     FAILWITH }
        |   { IF { PUSH string "The image bid contract is now closed" ;
        |          FAILWITH }
        |        { UNIT } }""".stripMargin
    }

  it should "render empty MichelsonSchema" in {
      MichelsonSchema.empty.render() shouldBe ""
    }

  it should "render MichelsonInstruction with an annotation" in {
      val michelsonInstruction = MichelsonSingleInstruction("CAR", annotations = List("@pointcolor"))

      michelsonInstruction.render() shouldBe "CAR @pointcolor"
    }

  it should "render complex MichelsonCode" in {
      val michelsonExpression = MichelsonCode(
        List(
          MichelsonSingleInstruction("CDR"),
          MichelsonSingleInstruction("DUP"),
          MichelsonSingleInstruction("NIL", List(MichelsonType("operation"))),
          MichelsonInstructionSequence(
            List(
              MichelsonSingleInstruction(
                "DIP",
                List(
                  MichelsonInstructionSequence(
                    List(
                      MichelsonSingleInstruction(
                        "DIP",
                        List(MichelsonInstructionSequence(List(MichelsonSingleInstruction("DUP"))))
                      ),
                      MichelsonSingleInstruction("SWAP")
                    )
                  )
                )
              ),
              MichelsonSingleInstruction("SWAP")
            )
          ),
          MichelsonInstructionSequence(
            List(
              MichelsonSingleInstruction(
                "DIP",
                List(
                  MichelsonInstructionSequence(
                    List(
                      MichelsonSingleInstruction(
                        "DIP",
                        List(MichelsonInstructionSequence(List(MichelsonSingleInstruction("DUP"))))
                      ),
                      MichelsonSingleInstruction("NIL", List(MichelsonType("operation")))
                    )
                  )
                )
              ),
              MichelsonSingleInstruction("SWAP")
            )
          )
        )
      )

      michelsonExpression.render() shouldBe
        """CDR ;
        |       DUP ;
        |       NIL operation ;
        |       { DIP { DIP { DUP } ; SWAP } ; SWAP } ;
        |       { DIP { DIP { DUP } ; NIL operation } ; SWAP }""".stripMargin
    }
}
