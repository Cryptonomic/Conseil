package tech.cryptonomic.conseil.indexer.tezos.michelson

import org.scalatest._
import tech.cryptonomic.conseil.indexer.tezos.michelson.dto.MichelsonSchema

class JsonToMichelsonSpec extends FlatSpec with Matchers {

  "A JsonToMichelson" should "convert json to michelson format" in {
      val json =
        """
        |[
        |  {
        |    "prim": "parameter",
        |    "args": [
        |      {
        |        "prim": "key_hash"
        |      }
        |    ]
        |  },
        |  {
        |    "prim": "storage",
        |    "args": [
        |      {
        |        "prim": "pair",
        |        "args": [
        |          {
        |            "prim": "key_hash"
        |          },
        |          {
        |            "prim": "timestamp"
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
        |          "prim": "DUP"
        |        },
        |        {
        |          "prim": "DIP",
        |          "args": [
        |            [
        |              {
        |                "prim": "CDR"
        |              }
        |            ]
        |          ]
        |        },
        |        {
        |          "prim": "CAR"
        |        },
        |        [
        |          {
        |            "prim": "DIP",
        |            "args": [
        |              [
        |                {
        |                  "prim": "DUP"
        |                }
        |              ]
        |            ]
        |          },
        |          {
        |            "prim": "SWAP"
        |          }
        |        ],
        |        {
        |          "prim": "CDR"
        |        },
        |        {
        |          "prim": "NOW"
        |        },
        |        {
        |          "prim": "COMPARE"
        |        },
        |        {
        |          "prim": "LT"
        |        },
        |        {
        |          "prim": "IF",
        |          "args": [
        |            [
        |              {
        |                "prim": "PUSH",
        |                "args": [
        |                  {
        |                    "prim": "mutez"
        |                  },
        |                  {
        |                    "int": "1000"
        |                  }
        |                ]
        |              },
        |              {
        |                "prim": "AMOUNT"
        |              },
        |              {
        |                "prim": "COMPARE"
        |              },
        |              {
        |                "prim": "LT"
        |              },
        |              {
        |                "prim": "IF",
        |                "args": [
        |                  [
        |                    {
        |                      "prim": "PUSH",
        |                      "args": [
        |                        {
        |                          "prim": "string"
        |                        },
        |                        {
        |                          "string": "You must bet at least 0.001 tz"
        |                        }
        |                      ]
        |                    },
        |                    {
        |                      "prim": "FAILWITH"
        |                    }
        |                  ],
        |                  [
        |                    [
        |                      {
        |                        "prim": "DIP",
        |                        "args": [
        |                          [
        |                            {
        |                              "prim": "DUP",
        |                              "annots": [
        |                                "@storage"
        |                              ]
        |                            }
        |                          ]
        |                        ]
        |                      },
        |                      {
        |                        "prim": "SWAP"
        |                      }
        |                    ],
        |                    {
        |                      "prim": "CDR"
        |                    },
        |                    [
        |                      {
        |                        "prim": "DIP",
        |                        "args": [
        |                          [
        |                            {
        |                              "prim": "DUP",
        |                              "annots": [
        |                                "@parameter"
        |                              ]
        |                            }
        |                          ]
        |                        ]
        |                      },
        |                      {
        |                        "prim": "SWAP"
        |                      }
        |                    ],
        |                    {
        |                      "prim": "PAIR",
        |                      "annots": [
        |                        "@storage"
        |                      ]
        |                    },
        |                    {
        |                      "prim": "DUP"
        |                    },
        |                    {
        |                      "prim": "CAR"
        |                    },
        |                    {
        |                      "prim": "SWAP"
        |                    },
        |                    {
        |                      "prim": "DROP"
        |                    },
        |                    {
        |                      "prim": "PUSH",
        |                      "args": [
        |                        {
        |                          "prim": "int"
        |                        },
        |                        {
        |                          "int": "86400"
        |                        }
        |                      ]
        |                    },
        |                    {
        |                      "prim": "NOW"
        |                    },
        |                    {
        |                      "prim": "ADD"
        |                    },
        |                    {
        |                      "prim": "SWAP"
        |                    },
        |                    {
        |                      "prim": "PAIR",
        |                      "annots": [
        |                        "@storage"
        |                      ]
        |                    },
        |                    {
        |                      "prim": "NIL",
        |                      "args": [
        |                        {
        |                          "prim": "operation"
        |                        }
        |                      ]
        |                    },
        |                    {
        |                      "prim": "PAIR"
        |                    }
        |                  ]
        |                ]
        |              }
        |            ],
        |            [
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
        |              ],
        |              {
        |                "prim": "CAR"
        |              },
        |              {
        |                "prim": "IMPLICIT_ACCOUNT",
        |                "annots": [
        |                  "@destination"
        |                ]
        |              },
        |              {
        |                "prim": "BALANCE",
        |                "annots": [
        |                  "@transfer"
        |                ]
        |              },
        |              {
        |                "prim": "UNIT"
        |              },
        |              {
        |                "prim": "TRANSFER_TOKENS",
        |                "annots": [
        |                  "@op1"
        |                ]
        |              },
        |              [
        |                {
        |                  "prim": "DIP",
        |                  "args": [
        |                    [
        |                      [
        |                        {
        |                          "prim": "DIP",
        |                          "args": [
        |                            [
        |                              {
        |                                "prim": "DUP",
        |                                "annots": [
        |                                  "@storage"
        |                                ]
        |                              }
        |                            ]
        |                          ]
        |                        },
        |                        {
        |                          "prim": "SWAP"
        |                        }
        |                      ]
        |                    ]
        |                  ]
        |                },
        |                {
        |                  "prim": "SWAP"
        |                }
        |              ],
        |              {
        |                "prim": "CDR"
        |              },
        |              {
        |                "prim": "PUSH",
        |                "args": [
        |                  {
        |                    "prim": "key_hash"
        |                  },
        |                  {
        |                    "string": "tz1TheGameisResetSendMoneyNowxxN7HgB"
        |                  }
        |                ]
        |              },
        |              {
        |                "prim": "PAIR",
        |                "annots": [
        |                  "@storage"
        |                ]
        |              },
        |              {
        |                "prim": "DUP"
        |              },
        |              {
        |                "prim": "CAR"
        |              },
        |              {
        |                "prim": "SWAP"
        |              },
        |              {
        |                "prim": "DROP"
        |              },
        |              {
        |                "prim": "PUSH",
        |                "args": [
        |                  {
        |                    "prim": "timestamp"
        |                  },
        |                  {
        |                    "string": "2600-01-01T00:00:00Z"
        |                  }
        |                ]
        |              },
        |              {
        |                "prim": "SWAP"
        |              },
        |              {
        |                "prim": "PAIR",
        |                "annots": [
        |                  "@storage"
        |                ]
        |              },
        |              {
        |                "prim": "NIL",
        |                "args": [
        |                  {
        |                    "prim": "operation"
        |                  }
        |                ]
        |              },
        |              [
        |                {
        |                  "prim": "DIP",
        |                  "args": [
        |                    [
        |                      [
        |                        {
        |                          "prim": "DIP",
        |                          "args": [
        |                            [
        |                              {
        |                                "prim": "DUP",
        |                                "annots": [
        |                                  "@op1"
        |                                ]
        |                              }
        |                            ]
        |                          ]
        |                        },
        |                        {
        |                          "prim": "SWAP"
        |                        }
        |                      ]
        |                    ]
        |                  ]
        |                },
        |                {
        |                  "prim": "SWAP"
        |                }
        |              ],
        |              {
        |                "prim": "DIP",
        |                "args": [
        |                  [
        |                    {
        |                      "prim": "DIP",
        |                      "args": [
        |                        [
        |                          {
        |                            "prim": "DIP",
        |                            "args": [
        |                              [
        |                                {
        |                                  "prim": "DROP"
        |                                }
        |                              ]
        |                            ]
        |                          }
        |                        ]
        |                      ]
        |                    }
        |                  ]
        |                ]
        |              },
        |              {
        |                "prim": "CONS"
        |              },
        |              {
        |                "prim": "PAIR"
        |              }
        |            ]
        |          ]
        |        },
        |        {
        |          "prim": "DIP",
        |          "args": [
        |            [
        |              {
        |                "prim": "DROP"
        |              },
        |              {
        |                "prim": "DROP"
        |              }
        |            ]
        |          ]
        |        }
        |      ]
        |    ]
        |  }
        |]""".stripMargin

      val result =
        """parameter key_hash;
        |storage (pair key_hash timestamp);
        |code { DUP ;
        |       DIP { CDR } ;
        |       CAR ;
        |       { DIP { DUP } ; SWAP } ;
        |       CDR ;
        |       NOW ;
        |       COMPARE ;
        |       LT ;
        |       IF { PUSH mutez 1000 ;
        |            AMOUNT ;
        |            COMPARE ;
        |            LT ;
        |            IF { PUSH string "You must bet at least 0.001 tz" ;
        |                 FAILWITH }
        |               { { DIP { DUP @storage } ; SWAP } ;
        |                 CDR ;
        |                 { DIP { DUP @parameter } ; SWAP } ;
        |                 PAIR @storage ;
        |                 DUP ;
        |                 CAR ;
        |                 SWAP ;
        |                 DROP ;
        |                 PUSH int 86400 ;
        |                 NOW ;
        |                 ADD ;
        |                 SWAP ;
        |                 PAIR @storage ;
        |                 NIL operation ;
        |                 PAIR } }
        |          { { DIP { DUP } ; SWAP } ;
        |            CAR ;
        |            IMPLICIT_ACCOUNT @destination ;
        |            BALANCE @transfer ;
        |            UNIT ;
        |            TRANSFER_TOKENS @op1 ;
        |            { DIP { { DIP { DUP @storage } ; SWAP } } ; SWAP } ;
        |            CDR ;
        |            PUSH key_hash "tz1TheGameisResetSendMoneyNowxxN7HgB" ;
        |            PAIR @storage ;
        |            DUP ;
        |            CAR ;
        |            SWAP ;
        |            DROP ;
        |            PUSH timestamp "2600-01-01T00:00:00Z" ;
        |            SWAP ;
        |            PAIR @storage ;
        |            NIL operation ;
        |            { DIP { { DIP { DUP @op1 } ; SWAP } } ; SWAP } ;
        |            DIP { DIP { DIP { DROP } } } ;
        |            CONS ;
        |            PAIR } ;
        |       DIP { DROP ; DROP } }""".stripMargin

      JsonToMichelson.convert[MichelsonSchema](json) should equal(Right(result))
    }
}
