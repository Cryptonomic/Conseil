package tech.cryptonomic.conseil.tezos.michelson.parser

import org.scalatest._
import tech.cryptonomic.conseil.tezos.michelson.dto._
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser.{ParserError, parse}

class JsonParserSpec extends FlatSpec with Matchers {

  it should "parse one-argument MichelsonType" in {
    val json = """{"prim": "contract"}"""

    parse[MichelsonExpression](json) should equal(Right(MichelsonType("contract")))
  }

  it should "parse two-argument MichelsonType" in {
    val json =
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
        |}""".stripMargin

    parse[MichelsonExpression](json) should equal(Right(
      MichelsonType("pair", List(
        MichelsonType("int"),
        MichelsonType("address")))))
  }

  it should "parse complex MichelsonType" in {
    val json =
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
        |}""".stripMargin

    parse[MichelsonExpression](json) should equal(Right(
      MichelsonType("contract", List(
        MichelsonType("or", List(
          MichelsonType("option", List(
            MichelsonType("address"))),
          MichelsonType("int")))))))
  }

  it should "parse MichelsonType with annotation" in {
    val json =
      """{
        |  "prim": "int",
        |  "annots": [
        |    ":p"
        |  ]
        |}""".stripMargin

    parse[MichelsonExpression](json) should equal(Right(
      MichelsonType(prim = "int", annotations = List(":p"))))
  }

  it should "parse MichelsonInstruction with only one simple instruction" in {
    val json = """{"prim": "DUP"}"""

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonSingleInstruction("DUP")))
  }

  it should "parse MichelsonInstructionSequence" in {
    val json = """[{"prim": "CDR"}, {"prim": "DUP"}]"""

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonInstructionSequence(List(MichelsonSingleInstruction("CDR"), MichelsonSingleInstruction("DUP")))))
  }

  it should "parse typed MichelsonInstruction" in {
    val json = """{"prim": "NIL", "args": [{"prim": "operation"}]}"""

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonSingleInstruction("NIL", List(MichelsonType("operation")))))
  }

  it should "parse MichelsonInstruction with annotation" in {
    val json = """{"prim": "CAR", "annots": ["@pointcolor"]}"""

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonSingleInstruction("CAR", annotations = List("@pointcolor"))))
  }

  it should "parse complex MichelsonInstruction" in {
    val json = """[{"prim": "DIP", "args": [[{"prim": "DUP"}]]}]"""

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonInstructionSequence(List(
        MichelsonSingleInstruction("DIP", List(MichelsonInstructionSequence(List(
          MichelsonSingleInstruction("DUP")))))))))
  }

  it should "parse MichelsonInstruction typed with int data" in {
    val json =
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
        |}]""".stripMargin

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonInstructionSequence(List(
        MichelsonSingleInstruction("PUSH", List(
          MichelsonType("mutez"),
          MichelsonIntConstant(0)))))))
  }

  it should "parse MichelsonInstruction typed with string data" in {
    val json =
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
        |}]""".stripMargin

    parse[MichelsonInstruction](json) should equal(Right(
        MichelsonInstructionSequence(List(
            MichelsonSingleInstruction("PUSH", List(
                MichelsonType("mutez"),
                MichelsonStringConstant("0")))))))
  }

  it should "parse MichelsonInstruction typed with bytes data" in {
    val json =
      """{
        |  "prim": "PUSH",
        |  "args": [
        |    {
        |      "prim": "bytes"
        |    },
        |    {
        |      "bytes": "0500"
        |    }
        |  ]
        |}""".stripMargin

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonSingleInstruction("PUSH", List(
        MichelsonType("bytes"),
        MichelsonBytesConstant("0500")))))
  }

  it should "parse double embedded MichelsonInstruction" in {
    val json =
      """[
        |  {
        |    "prim": "IF_NONE",
        |    "args": [
        |      [
        |        [
        |          {
        |            "prim": "UNIT"
        |          },
        |          {
        |            "prim": "FAILWITH"
        |          }
        |        ]
        |      ]
        |    ]
        |  }
        |]""".stripMargin

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonInstructionSequence(List(
        MichelsonSingleInstruction("IF_NONE", List(MichelsonInstructionSequence(List(
          MichelsonInstructionSequence(List(
            MichelsonSingleInstruction("UNIT"),
            MichelsonSingleInstruction("FAILWITH")))))))))))
  }

  it should "parse empty MichelsonInstruction" in {
    val json =
      """[
        |  {
        |    "prim": "IF_NONE",
        |    "args": [
        |      [],
        |      [
        |        [
        |          {
        |            "prim": "UNIT"
        |          },
        |          {
        |            "prim": "FAILWITH"
        |          }
        |        ]
        |      ],
        |      []
        |    ]
        |  }
        |]""".stripMargin

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonInstructionSequence(List(
        MichelsonSingleInstruction("IF_NONE", List(
          MichelsonEmptyInstruction, MichelsonInstructionSequence(List(
            MichelsonInstructionSequence(List(
              MichelsonSingleInstruction("UNIT"),
              MichelsonSingleInstruction("FAILWITH"))))),
          MichelsonEmptyInstruction))))))
  }

  it should "parse empty MichelsonInstruction when it appears alone" in {
    val json =
      """[
        |  {
        |    "prim": "IF_NONE",
        |    "args": [
        |      []
        |    ]
        |  }
        |]""".stripMargin

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonInstructionSequence(List(
        MichelsonSingleInstruction("IF_NONE", List(
          MichelsonEmptyInstruction))))))
  }

  it should "parse empty MichelsonExpression" in {
    val json =
      """{
        |  "prim": "Pair",
        |  "args": [
        |    {
        |      "int": "0"
        |    },
        |    []
        |  ]
        |}""".stripMargin

    parse[MichelsonExpression](json) should equal(Right(
      MichelsonType("Pair", List(
        MichelsonIntConstant(0),
        MichelsonEmptyExpression))))
  }

  it should "parse LAMBDA MichelsonInstruction" in {
    val json =
      """{
        |  "prim": "LAMBDA",
        |  "args": [
        |    {
        |      "prim": "address"
        |    },
        |    {
        |      "prim": "contract",
        |      "args": [
        |        {
        |          "prim": "unit"
        |        }
        |      ]
        |    },
        |    [
        |      {
        |        "prim": "DUP"
        |      }
        |    ]
        |  ]
        |}""".stripMargin

    parse[MichelsonInstruction](json) should equal(Right(
      MichelsonSingleInstruction("LAMBDA", List(
        MichelsonType("address"),
        MichelsonType("contract", List(
          MichelsonType("unit"))), MichelsonInstructionSequence(List(
        MichelsonSingleInstruction("DUP")))))))
  }

  it should "convert simplest json to MichelsonSchema" in {

    val json =
    """[
        |  {
        |    "prim": "parameter",
        |    "args": [
        |      {
        |        "prim": "int"
        |      }
        |    ]
        |  },
        |  {
        |    "prim": "storage",
        |    "args": [
        |      {
        |        "prim": "int"
        |      }
        |    ]
        |  },
        |  {
        |    "prim": "code",
        |    "args": [
        |      [
        |        {
        |          "prim": "DUP"
        |        }
        |      ]
        |    ]
        |  }
        |]""".stripMargin

    parse[MichelsonSchema](json) should equal(Right(MichelsonSchema(
        MichelsonType("int"),
        MichelsonType("int"),
        MichelsonCode(List(MichelsonSingleInstruction("DUP"))))))
  }

  it should "parse MichelsonCode" in {
    val json = """[{"prim": "DUP"}]"""

    parse[MichelsonCode](json) should equal(Right(MichelsonCode(
        List(MichelsonSingleInstruction("DUP")))))
  }

  it should "give meaningful error in case of json without parameter section" in {
    val json = """[{"prim": "storage", "args": []}]"""

    parse[MichelsonSchema](json) should equal(Left(ParserError("No expression parameter found")))
  }

  it should "give meaningful error in case of json without code section" in {
    val json = """[{"prim": "parameter", "args": [{"prim": "unit"}]}, {"prim": "storage", "args": [{"prim": "unit"}]}]"""

    parse[MichelsonSchema](json) should equal(Left(ParserError("No code code found")))
  }

  it should "parse empty schema" in {
    val json = """[]"""

    parse[MichelsonSchema](json) should equal(Right(MichelsonSchema.empty))
  }

  it should "convert complex json to MichelsonSchema" in {

    val json =
      """[
        |  {
        |    "prim": "parameter",
        |    "args": [
        |      {
        |        "prim": "unit"
        |      }
        |    ]
        |  },
        |  {
        |    "prim": "storage",
        |    "args": [
        |      {
        |        "prim": "contract",
        |        "args": [
        |          {
        |            "prim": "or",
        |            "args": [
        |              {
        |                "prim": "option",
        |                "args": [
        |                  {
        |                    "prim": "address"
        |                  }
        |                ]
        |              },
        |              {
        |                "prim": "or",
        |                "args": [
        |                  {
        |                    "prim": "pair",
        |                    "args": [
        |                      {
        |                        "prim": "option",
        |                        "args": [
        |                          {
        |                            "prim": "address"
        |                          }
        |                        ]
        |                      },
        |                      {
        |                        "prim": "option",
        |                        "args": [
        |                          {
        |                            "prim": "mutez"
        |                          }
        |                        ]
        |                      }
        |                    ]
        |                  },
        |                  {
        |                    "prim": "or",
        |                    "args": [
        |                      {
        |                        "prim": "mutez"
        |                      },
        |                      {
        |                        "prim": "or",
        |                        "args": [
        |                          {
        |                            "prim": "pair",
        |                            "args": [
        |                              {
        |                                "prim": "option",
        |                                "args": [
        |                                  {
        |                                    "prim": "address"
        |                                  }
        |                                ]
        |                              },
        |                              {
        |                                "prim": "option",
        |                                "args": [
        |                                  {
        |                                    "prim": "mutez"
        |                                  }
        |                                ]
        |                              }
        |                            ]
        |                          },
        |                          {
        |                            "prim": "address"
        |                          }
        |                        ]
        |                      }
        |                    ]
        |                  }
        |                ]
        |              }
        |            ]
        |          }
        |        ]
        |      }
        |    ]
        |  },
        |  {
        |    "prim": "code",
        |    "args": [
        |      [
        |        {
        |          "prim": "CDR"
        |        },
        |        {
        |          "prim": "DUP"
        |        },
        |        {
        |          "prim": "NIL",
        |          "args": [
        |            {
        |              "prim": "operation"
        |            }
        |          ]
        |        },
        |        [
        |          {
        |            "prim": "DIP",
        |            "args": [
        |              [
        |                {
        |                  "prim": "DIP",
        |                  "args": [
        |                    [
        |                      {
        |                        "prim": "DUP"
        |                      }
        |                    ]
        |                  ]
        |                },
        |                {
        |                  "prim": "SWAP"
        |                }
        |              ]
        |            ]
        |          },
        |          {
        |            "prim": "SWAP"
        |          },
        |          {
        |            "prim": "NIL",
        |            "args": [
        |              {
        |                "prim": "operation"
        |              }
        |            ]
        |          }
        |        ]
        |      ]
        |    ]
        |  }
        |]""".stripMargin

    parse[MichelsonSchema](json) should equal(Right(MichelsonSchema(
      MichelsonType("unit"),
      MichelsonType("contract", List(
        MichelsonType("or", List(
          MichelsonType("option", List(
            MichelsonType("address"))),
          MichelsonType("or", List(
            MichelsonType("pair", List(
              MichelsonType("option", List(
                MichelsonType("address"))),
              MichelsonType("option", List(
                MichelsonType("mutez"))))),
            MichelsonType("or", List(
              MichelsonType("mutez"),
              MichelsonType("or", List(
                MichelsonType("pair", List(
                  MichelsonType("option", List(
                    MichelsonType("address"))),
                  MichelsonType("option", List(
                    MichelsonType("mutez"))))),
                MichelsonType("address"))))))))))),
      MichelsonCode(List(
        MichelsonSingleInstruction("CDR"),
        MichelsonSingleInstruction("DUP"),
        MichelsonSingleInstruction("NIL", List(
          MichelsonType("operation"))),
        MichelsonInstructionSequence(List(
          MichelsonSingleInstruction("DIP", List(MichelsonInstructionSequence(List(
            MichelsonSingleInstruction("DIP", List(MichelsonInstructionSequence(List(
              MichelsonSingleInstruction("DUP"))))),
            MichelsonSingleInstruction("SWAP"))))),
          MichelsonSingleInstruction("SWAP"),
          MichelsonSingleInstruction("NIL", List(
            MichelsonType("operation"))))))))))
  }
}
