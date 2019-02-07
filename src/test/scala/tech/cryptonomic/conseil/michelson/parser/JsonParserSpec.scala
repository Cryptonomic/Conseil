package tech.cryptonomic.conseil.michelson.parser

import org.scalatest._
import tech.cryptonomic.conseil.michelson.dto._

class JsonParserSpec extends FlatSpec with Matchers {

  private val parser = new JsonParser()

  it should "parse one-argument MichelsonType" in {
    val json = Code(parameter = """{"prim": "contract"}""").toJson

    parser.parse(json).map(_.parameter) should equal(Right(MichelsonType("contract")))
  }

  it should "parse two-argument MichelsonType" in {
    val json = Code(parameter =
      """{
        |  "prim": "pair",
        |  "args": [
        |    {
        |      "prim": "int"
        |    },
        |    {
        |      "prim": "address"
        |    }
        |  ]
        |}""").toJson

    parser.parse(json).map(_.parameter) should equal(Right(
      MichelsonType("pair", Seq(MichelsonType("int"), MichelsonType("address")))))
  }

  it should "parse complex MichelsonType" in {
    val json = Code(parameter =
      """{
        |  "prim": "contract",
        |  "args": [
        |    {
        |      "prim": "or",
        |      "args": [
        |        {
        |          "prim": "option",
        |          "args": [
        |            {
        |              "prim": "address"
        |            }
        |          ]
        |        },
        |        {
        |          "prim": "int"
        |        }
        |      ]
        |    }
        |  ]
        |}""").toJson

    parser.parse(json).map(_.parameter) should equal(Right(
      MichelsonType("contract", Seq(MichelsonType("or", Seq(
        MichelsonType("option", Seq(MichelsonType("address", Seq()))),
        MichelsonType("int", Seq())))))))
  }

  it should "parse MichelsonCode with only one simple instruction" in {
    val json = Code(code = """[{"prim": "DUP"}]""").toJson

    parser.parse(json).map(_.code) should equal(Right(
      MichelsonCode(Seq(MichelsonSimpleInstruction("DUP")))))
  }

  it should "parse MichelsonCode with two simple instructions" in {
    val json = Code(code = """[{"prim": "CDR"}, {"prim": "DUP"}]""").toJson

    parser.parse(json).map(_.code) should equal(Right(
      MichelsonCode(Seq(MichelsonSimpleInstruction("CDR"), MichelsonSimpleInstruction("DUP")))))
  }

  it should "parse MichelsonCode with typed instruction" in {
    val json = Code(code = """[{"prim": "NIL", "args": [{"prim": "operation"}]}]""").toJson

    parser.parse(json).map(_.code) should equal(Right(
      MichelsonCode(Seq(MichelsonSimpleInstruction("NIL", Some(MichelsonType("operation")))))))
  }

  it should "parse MichelsonCode with instruction sequence" in {
    val json = Code(code = """[[{"prim": "DIP"}, {"prim": "SWAP"}]]""").toJson

    parser.parse(json).map(_.code) should equal(Right(
      MichelsonCode(Seq(
        MichelsonInstructionSequence(Seq(
          MichelsonSimpleInstruction("DIP"),
          MichelsonSimpleInstruction("SWAP")))))))
  }

  it should "parse MichelsonCode with complex instruction" in {
    val json = Code(code = """[[{"prim": "DIP", "args": [[{"prim": "DUP"}]]}]]""").toJson

    parser.parse(json).map(_.code) should equal(Right(
      MichelsonCode(Seq(
      MichelsonInstructionSequence(Seq(
        MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(Seq(
          MichelsonSimpleInstruction("DUP"))))))))))
  }

  it should "convert simplest json to MichelsonSchema" in {

    val json = Code(parameter = """{"prim": "int"}""",
      storage = """{"prim": "int"}""",
      code = """[{"prim": "DUP"}]""").toJson

    parser.parse(json) should equal(Right(MichelsonSchema(
      MichelsonType("int", List()),
      MichelsonType("int", List()),
      MichelsonCode(List(MichelsonSimpleInstruction("DUP"))))))
  }

  it should "convert complex json to MichelsonSchema" in {

    val json =
      """{
        |  "code": [
        |      {
        |          "prim": "parameter",
        |          "args": [
        |              {
        |                  "prim": "unit"
        |              }
        |          ]
        |      },
        |      {
        |          "prim": "storage",
        |          "args": [
        |              {
        |                  "prim": "contract",
        |                  "args": [
        |                      {
        |                          "prim": "or",
        |                          "args": [
        |                              {
        |                                  "prim": "option",
        |                                  "args": [
        |                                      {
        |                                          "prim": "address"
        |                                      }
        |                                  ]
        |                              },
        |                              {
        |                                  "prim": "or",
        |                                  "args": [
        |                                      {
        |                                          "prim": "pair",
        |                                          "args": [
        |                                              {
        |                                                  "prim": "option",
        |                                                  "args": [
        |                                                      {
        |                                                          "prim": "address"
        |                                                      }
        |                                                  ]
        |                                              },
        |                                              {
        |                                                  "prim": "option",
        |                                                  "args": [
        |                                                      {
        |                                                          "prim": "mutez"
        |                                                      }
        |                                                  ]
        |                                              }
        |                                          ]
        |                                      },
        |                                      {
        |                                          "prim": "or",
        |                                          "args": [
        |                                              {
        |                                                  "prim": "mutez"
        |                                              },
        |                                              {
        |                                                  "prim": "or",
        |                                                  "args": [
        |                                                      {
        |                                                          "prim": "pair",
        |                                                          "args": [
        |                                                              {
        |                                                                  "prim": "option",
        |                                                                  "args": [
        |                                                                      {
        |                                                                          "prim": "address"
        |                                                                      }
        |                                                                  ]
        |                                                              },
        |                                                              {
        |                                                                  "prim": "option",
        |                                                                  "args": [
        |                                                                      {
        |                                                                          "prim": "mutez"
        |                                                                      }
        |                                                                  ]
        |                                                              }
        |                                                          ]
        |                                                      },
        |                                                      {
        |                                                          "prim": "address"
        |                                                      }
        |                                                  ]
        |                                              }
        |                                          ]
        |                                      }
        |                                  ]
        |                              }
        |                          ]
        |                      }
        |                  ]
        |              }
        |          ]
        |      },
        |      {
        |          "prim": "code",
        |          "args": [
        |              [
        |                  {
        |                      "prim": "CDR"
        |                  },
        |                  {
        |                      "prim": "DUP"
        |                  },
        |                  {
        |                      "prim": "NIL",
        |                      "args": [
        |                          {
        |                              "prim": "operation"
        |                          }
        |                      ]
        |                  },
        |                  [
        |                      {
        |                          "prim": "DIP",
        |                          "args": [
        |                              [
        |                                  {
        |                                      "prim": "DIP",
        |                                      "args": [
        |                                          [
        |                                              {
        |                                                  "prim": "DUP"
        |                                              }
        |                                          ]
        |                                      ]
        |                                  },
        |                                  {
        |                                      "prim": "SWAP"
        |                                  }
        |                              ]
        |                          ]
        |                      },
        |                      {
        |                          "prim": "SWAP"
        |                      },
        |                      {
        |                          "prim": "NIL",
        |                          "args": [
        |                              {
        |                                  "prim": "operation"
        |                              }
        |                          ]
        |                      }
        |                  ]
        |              ]
        |          ]
        |      }
        |  ]
        |}""".stripMargin

    parser.parse(json) should equal(Right(MichelsonSchema(
      MichelsonType("unit", List()),
      MichelsonType("contract", List(
        MichelsonType("or", List(
          MichelsonType("option", List(
            MichelsonType("address", List()))),
          MichelsonType("or", List(
            MichelsonType("pair", List(
              MichelsonType("option", List(
                MichelsonType("address", List()))),
              MichelsonType("option", List(
                MichelsonType("mutez", List()))))),
            MichelsonType("or", List(
              MichelsonType("mutez", List()),
              MichelsonType("or", List(
                MichelsonType("pair", List(
                  MichelsonType("option", List(
                    MichelsonType("address", List()))),
                  MichelsonType("option", List(
                    MichelsonType("mutez", List()))))),
                MichelsonType("address", List()))))))))))),
      MichelsonCode(List(
        MichelsonSimpleInstruction("CDR"),
        MichelsonSimpleInstruction("DUP"),
        MichelsonSimpleInstruction("NIL", Some(
          MichelsonType("operation"))),
        MichelsonInstructionSequence(Seq(
          MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(Seq(
            MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(Seq(
              MichelsonSimpleInstruction("DUP")))),
            MichelsonSimpleInstruction("SWAP")))),
          MichelsonSimpleInstruction("SWAP"),
          MichelsonSimpleInstruction("NIL", Some(
            MichelsonType("operation"))))))))))
  }

  case class Code(
                   parameter: String = """{"prim": "unit"}""",
                   storage: String = """{"prim": "unit"}""",
                   code: String = """[{"prim": "CDR"}]""") {

    def toJson: String =
      s"""{
         |  "code": [
         |      {
         |          "prim": "parameter",
         |          "args": [
         |              $parameter
         |          ]
         |      },
         |      {
         |          "prim": "storage",
         |          "args": [
         |              $storage
         |          ]
         |      },
         |      {
         |          "prim": "code",
         |          "args": [
         |              $code
         |          ]
         |      }
         |  ]
         |}""".stripMargin
  }

}
