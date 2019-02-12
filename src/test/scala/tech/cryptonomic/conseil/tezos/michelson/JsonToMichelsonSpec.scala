package tech.cryptonomic.conseil.tezos.michelson

import org.scalatest._
import tech.cryptonomic.conseil.tezos.michelson.JsonToMichelson.convert

class JsonToMichelsonSpec extends FlatSpec with Matchers {

  "A JsonToMichelson" should "convert json to michelson format" in {
    val json =
      """
        |{
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
        |                      }
        |                  ],
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
        |                                                  "prim": "DIP",
        |                                                  "args": [
        |                                                      [
        |                                                          {
        |                                                              "prim": "DROP"
        |                                                          }
        |                                                      ]
        |                                                  ]
        |                                              }
        |                                          ]
        |                                      ]
        |                                  }
        |                              ]
        |                          ]
        |                      }
        |                  ],
        |                  {
        |                      "prim": "AMOUNT"
        |                  },
        |                  {
        |                      "prim": "SENDER"
        |                  },
        |                  {
        |                      "prim": "SOME"
        |                  },
        |                  {
        |                      "prim": "LEFT",
        |                      "args": [
        |                          {
        |                              "prim": "or",
        |                              "args": [
        |                                  {
        |                                      "prim": "pair",
        |                                      "args": [
        |                                          {
        |                                              "prim": "option",
        |                                              "args": [
        |                                                  {
        |                                                      "prim": "address"
        |                                                  }
        |                                              ]
        |                                          },
        |                                          {
        |                                              "prim": "option",
        |                                              "args": [
        |                                                  {
        |                                                      "prim": "mutez"
        |                                                  }
        |                                              ]
        |                                          }
        |                                      ]
        |                                  },
        |                                  {
        |                                      "prim": "or",
        |                                      "args": [
        |                                          {
        |                                              "prim": "mutez"
        |                                          },
        |                                          {
        |                                              "prim": "or",
        |                                              "args": [
        |                                                  {
        |                                                      "prim": "pair",
        |                                                      "args": [
        |                                                          {
        |                                                              "prim": "option",
        |                                                              "args": [
        |                                                                  {
        |                                                                      "prim": "address"
        |                                                                  }
        |                                                              ]
        |                                                          },
        |                                                          {
        |                                                              "prim": "option",
        |                                                              "args": [
        |                                                                  {
        |                                                                      "prim": "mutez"
        |                                                                  }
        |                                                              ]
        |                                                          }
        |                                                      ]
        |                                                  },
        |                                                  {
        |                                                      "prim": "address"
        |                                                  }
        |                                              ]
        |                                          }
        |                                      ]
        |                                  }
        |                              ]
        |                          }
        |                      ]
        |                  },
        |                  {
        |                      "prim": "TRANSFER_TOKENS"
        |                  },
        |                  {
        |                      "prim": "CONS"
        |                  },
        |                  {
        |                      "prim": "PAIR"
        |                  }
        |              ]
        |          ]
        |      }
        |  ]
        |}
        |""".stripMargin

    val result =
      """parameter unit;
        |storage (contract (or (option address) (or (pair (option address) (option mutez)) (or mutez (or (pair (option address) (option mutez)) address)))));
        |code { CDR ;
        |       DUP ;
        |       NIL operation ;
        |       { DIP { DIP { DUP } ; SWAP } ; SWAP } ;
        |       { DIP { DIP { DIP { DROP } } } } ;
        |       AMOUNT ;
        |       SENDER ;
        |       SOME ;
        |       LEFT (or (pair (option address) (option mutez)) (or mutez (or (pair (option address) (option mutez)) address))) ;
        |       TRANSFER_TOKENS ;
        |       CONS ;
        |       PAIR }""".stripMargin

    convert(json) should equal(Right(result))
  }
}
