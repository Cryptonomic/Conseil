package tech.cryptonomic.conseil.tezos.michelson.parser

import org.scalatest._
import tech.cryptonomic.conseil.tezos.michelson.dto._
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser.parse

class JsonParserSpec extends FlatSpec with Matchers {

  it should "parse one-argument MichelsonType" in {
    val json = Code(parameter = """{"prim": "contract"}""").toJson

    parse(json).map(_.parameter) should equal(Right(MichelsonType("contract")))
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

    parse(json).map(_.parameter) should equal(Right(
      MichelsonType("pair", List(
        MichelsonType("int"),
        MichelsonType("address")))))
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

    parse(json).map(_.parameter) should equal(Right(
      MichelsonType("contract", List(
        MichelsonType("or", List(
          MichelsonType("option", List(
            MichelsonType("address"))),
          MichelsonType("int")))))))
  }

  it should "parse MichelsonType with int data" in {
    val json = Code(code =
      """[{
        |  "prim": "PUSH",
        |  "args": [
        |    {
        |      "prim": "mutez"
        |    },
        |    {
        |      "int": "0"
        |    }
        |  ]
        |}]""").toJson

    parse(json).map(_.code) should equal(Right(
      MichelsonCode(List(
        MichelsonSimpleInstruction("PUSH", List(
          MichelsonType("mutez"),
          MichelsonIntConstant(0)))))))
  }

  it should "parse MichelsonType with string data" in {
    val json = Code(code =
      """[{
        |  "prim": "PUSH",
        |  "args": [
        |    {
        |      "prim": "mutez"
        |    },
        |    {
        |      "string": "0"
        |    }
        |  ]
        |}]""").toJson

    parse(json).map(_.code) should equal(Right(
      MichelsonCode(List(
        MichelsonSimpleInstruction("PUSH", List(
          MichelsonType("mutez"),
          MichelsonStringConstant("0")))))))
  }

  it should "parse MichelsonCode with only one simple instruction" in {
    val json = Code(code = """[{"prim": "DUP"}]""").toJson

    parse(json).map(_.code) should equal(Right(
      MichelsonCode(List(MichelsonSimpleInstruction("DUP")))))
  }

  it should "parse MichelsonCode with two simple instructions" in {
    val json = Code(code = """[{"prim": "CDR"}, {"prim": "DUP"}]""").toJson

    parse(json).map(_.code) should equal(Right(
      MichelsonCode(List(MichelsonSimpleInstruction("CDR"), MichelsonSimpleInstruction("DUP")))))
  }

  it should "parse MichelsonCode with typed instruction" in {
    val json = Code(code = """[{"prim": "NIL", "args": [{"prim": "operation"}]}]""").toJson

    parse(json).map(_.code) should equal(Right(
      MichelsonCode(List(MichelsonSimpleInstruction("NIL", List(MichelsonType("operation")))))))
  }

  it should "parse MichelsonCode with instruction sequence (represented in JSON with double brackets)" in {
    val json = Code(code = """[[{"prim": "DIP"}, {"prim": "SWAP"}]]""").toJson

    parse(json).map(_.code) should equal(Right(
      MichelsonCode(List(
        MichelsonInstructionSequence(List(
          MichelsonSimpleInstruction("DIP"),
          MichelsonSimpleInstruction("SWAP")))))))
  }

  it should "parse MichelsonCode with complex instruction" in {
    val json = Code(code = """[[{"prim": "DIP", "args": [[{"prim": "DUP"}]]}]]""").toJson

    parse(json).map(_.code) should equal(Right(
      MichelsonCode(List(
      MichelsonInstructionSequence(List(
        MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(List(
          MichelsonSimpleInstruction("DUP"))))))))))
  }

  it should "convert simplest json to MichelsonSchema" in {

    val json = Code(parameter = """{"prim": "int"}""",
      storage = """{"prim": "int"}""",
      code = """[{"prim": "DUP"}]""").toJson

    parse(json) should equal(Right(MichelsonSchema(
      MichelsonType("int"),
      MichelsonType("int"),
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

    parse(json) should equal(Right(MichelsonSchema(
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
        MichelsonSimpleInstruction("NIL", List(
          MichelsonType("operation"))),
        MichelsonInstructionSequence(List(
          MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(List(
            MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(List(
              MichelsonSimpleInstruction("DUP")))),
            MichelsonSimpleInstruction("SWAP")))),
          MichelsonSimpleInstruction("SWAP"),
          MichelsonSimpleInstruction("NIL", List(
            MichelsonType("operation"))))))))))
  }

  private case class Code(
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
