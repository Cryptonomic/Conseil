package tech.cryptonomic.conseil.michelson.parser

import org.scalatest._
import tech.cryptonomic.conseil.michelson.dto._

class JsonParserSpec extends FlatSpec with Matchers {

  "A JsonToMichelson" should "convert json to domain objects" in {

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
        |}
        |""".stripMargin

    new JsonParser().parse(json) should equal(Right(MichelsonSchema(
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
        MichelsonSimpleInstruction("DUP", None),
        MichelsonSimpleInstruction("NIL", Some(MichelsonType("operation"))),
        MichelsonInstructionSequence(Seq(
          MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(Seq(
            MichelsonComplexInstruction("DIP", MichelsonInstructionSequence(Seq(
              MichelsonSimpleInstruction("DUP")))),
            MichelsonSimpleInstruction("SWAP")))),
          MichelsonSimpleInstruction("SWAP"),
          MichelsonSimpleInstruction("NIL", Some(MichelsonType("operation"))))))))))
  }
}
