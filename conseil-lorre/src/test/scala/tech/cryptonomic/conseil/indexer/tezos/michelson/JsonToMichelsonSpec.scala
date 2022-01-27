package tech.cryptonomic.conseil.indexer.tezos.michelson

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import tech.cryptonomic.conseil.indexer.tezos.michelson.dto.MichelsonSchema
import tech.cryptonomic.conseil.common.testkit.LoggingTestSupport

class JsonToMichelsonSpec extends AnyFlatSpec with Matchers with LoggingTestSupport {

  it should "convert json to michelson format" in {
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

    it should "convert MichelsonSchema with views" in {
      val json =
        """
          |[
          |   {
          |      "prim":"parameter",
          |      "args":[
          |         {
          |            "prim":"or",
          |            "args":[
          |               {
          |                  "prim":"address",
          |                  "annots":[
          |                     "%setArchetype"
          |                  ]
          |               },
          |               {
          |                  "prim":"pair",
          |                  "args":[
          |                     {
          |                        "prim":"nat",
          |                        "annots":[
          |                           "%id"
          |                        ]
          |                     },
          |                     {
          |                        "prim":"address",
          |                        "annots":[
          |                           "%mintingValidator"
          |                        ]
          |                     },
          |                     {
          |                        "prim":"nat",
          |                        "annots":[
          |                           "%imaxBalanceAllowed"
          |                        ]
          |                     }
          |                  ],
          |                  "annots":[
          |                     "%add"
          |                  ]
          |               }
          |            ]
          |         }
          |      ]
          |   },
          |   {
          |      "prim":"storage",
          |      "args":[
          |         {
          |            "prim":"pair",
          |            "args":[
          |               {
          |                  "prim":"address",
          |                  "annots":[
          |                     "%admin"
          |                  ]
          |               },
          |               {
          |                  "prim":"option",
          |                  "args":[
          |                     {
          |                        "prim":"address"
          |                     }
          |                  ],
          |                  "annots":[
          |                     "%archetype"
          |                  ]
          |               },
          |               {
          |                  "prim":"big_map",
          |                  "args":[
          |                     {
          |                        "prim":"nat"
          |                     },
          |                     {
          |                        "prim":"pair",
          |                        "args":[
          |                           {
          |                              "prim":"address",
          |                              "annots":[
          |                                 "%archValidator"
          |                              ]
          |                           },
          |                           {
          |                              "prim":"nat",
          |                              "annots":[
          |                                 "%maxBalanceAllowed"
          |                              ]
          |                           }
          |                        ]
          |                     }
          |                  ],
          |                  "annots":[
          |                     "%archetypeLedger"
          |                  ]
          |               }
          |            ]
          |         }
          |      ]
          |   },
          |   {
          |      "prim":"code",
          |      "args":[
          |         [
          |            [
          |               [
          |                  {
          |                     "prim":"DUP"
          |                  },
          |                  {
          |                     "prim":"CAR"
          |                  },
          |                  {
          |                     "prim":"DIP",
          |                     "args":[
          |                        [
          |                           {
          |                              "prim":"CDR"
          |                           }
          |                        ]
          |                     ]
          |                  }
          |               ]
          |            ],
          |            {
          |               "prim":"DIP",
          |               "args":[
          |                  [
          |                     [
          |                        [
          |                           {
          |                              "prim":"DUP"
          |                           },
          |                           {
          |                              "prim":"CAR"
          |                           },
          |                           {
          |                              "prim":"DIP",
          |                              "args":[
          |                                 [
          |                                    {
          |                                       "prim":"CDR"
          |                                    }
          |                                 ]
          |                              ]
          |                           }
          |                        ]
          |                     ],
          |                     {
          |                        "prim":"SWAP"
          |                     },
          |                     [
          |                        [
          |                           {
          |                              "prim":"DUP"
          |                           },
          |                           {
          |                              "prim":"CAR"
          |                           },
          |                           {
          |                              "prim":"DIP",
          |                              "args":[
          |                                 [
          |                                    {
          |                                       "prim":"CDR"
          |                                    }
          |                                 ]
          |                              ]
          |                           }
          |                        ]
          |                     ],
          |                     {
          |                        "prim":"SWAP"
          |                     }
          |                  ]
          |               ]
          |            },
          |            {
          |               "prim":"IF_LEFT",
          |               "args":[
          |                  [
          |                     {
          |                        "prim":"DIG",
          |                        "args":[
          |                           {
          |                              "int":"3"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DUP"
          |                     },
          |                     {
          |                        "prim":"DUG",
          |                        "args":[
          |                           {
          |                              "int":"4"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"SENDER"
          |                     },
          |                     {
          |                        "prim":"COMPARE"
          |                     },
          |                     {
          |                        "prim":"EQ"
          |                     },
          |                     {
          |                        "prim":"NOT"
          |                     },
          |                     {
          |                        "prim":"IF",
          |                        "args":[
          |                           [
          |                              {
          |                                 "prim":"PUSH",
          |                                 "args":[
          |                                    {
          |                                       "prim":"string"
          |                                    },
          |                                    {
          |                                       "string":"InvalidCaller"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"FAILWITH"
          |                              }
          |                           ],
          |                           [
          |
          |                           ]
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DUP"
          |                     },
          |                     {
          |                        "prim":"SOME"
          |                     },
          |                     {
          |                        "prim":"DIP",
          |                        "args":[
          |                           [
          |                              {
          |                                 "prim":"DIG",
          |                                 "args":[
          |                                    {
          |                                       "int":"2"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"DROP"
          |                              }
          |                           ]
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DUG",
          |                        "args":[
          |                           {
          |                              "int":"2"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DROP"
          |                     },
          |                     {
          |                        "prim":"SWAP"
          |                     },
          |                     {
          |                        "prim":"PAIR"
          |                     },
          |                     {
          |                        "prim":"SWAP"
          |                     },
          |                     {
          |                        "prim":"PAIR"
          |                     },
          |                     {
          |                        "prim":"NIL",
          |                        "args":[
          |                           {
          |                              "prim":"operation"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"PAIR"
          |                     }
          |                  ],
          |                  [
          |                     [
          |                        [
          |                           {
          |                              "prim":"DUP"
          |                           },
          |                           {
          |                              "prim":"CAR"
          |                           },
          |                           {
          |                              "prim":"DIP",
          |                              "args":[
          |                                 [
          |                                    {
          |                                       "prim":"CDR"
          |                                    }
          |                                 ]
          |                              ]
          |                           }
          |                        ]
          |                     ],
          |                     {
          |                        "prim":"SWAP"
          |                     },
          |                     [
          |                        [
          |                           {
          |                              "prim":"DUP"
          |                           },
          |                           {
          |                              "prim":"CAR"
          |                           },
          |                           {
          |                              "prim":"DIP",
          |                              "args":[
          |                                 [
          |                                    {
          |                                       "prim":"CDR"
          |                                    }
          |                                 ]
          |                              ]
          |                           }
          |                        ]
          |                     ],
          |                     {
          |                        "prim":"SWAP"
          |                     },
          |                     {
          |                        "prim":"DIG",
          |                        "args":[
          |                           {
          |                              "int":"4"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DUP"
          |                     },
          |                     {
          |                        "prim":"DUG",
          |                        "args":[
          |                           {
          |                              "int":"5"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"IF_NONE",
          |                        "args":[
          |                           [
          |                              {
          |                                 "prim":"PUSH",
          |                                 "args":[
          |                                    {
          |                                       "prim":"string"
          |                                    },
          |                                    {
          |                                       "string":"NotFound"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"FAILWITH"
          |                              }
          |                           ],
          |                           [
          |
          |                           ]
          |                        ]
          |                     },
          |                     {
          |                        "prim":"SENDER"
          |                     },
          |                     {
          |                        "prim":"COMPARE"
          |                     },
          |                     {
          |                        "prim":"EQ"
          |                     },
          |                     {
          |                        "prim":"NOT"
          |                     },
          |                     {
          |                        "prim":"IF",
          |                        "args":[
          |                           [
          |                              {
          |                                 "prim":"PUSH",
          |                                 "args":[
          |                                    {
          |                                       "prim":"string"
          |                                    },
          |                                    {
          |                                       "string":"InvalidCaller"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"FAILWITH"
          |                              }
          |                           ],
          |                           [
          |
          |                           ]
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DIG",
          |                        "args":[
          |                           {
          |                              "int":"3"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DUP"
          |                     },
          |                     {
          |                        "prim":"DUG",
          |                        "args":[
          |                           {
          |                              "int":"4"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DIG",
          |                        "args":[
          |                           {
          |                              "int":"3"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DUP"
          |                     },
          |                     {
          |                        "prim":"DUG",
          |                        "args":[
          |                           {
          |                              "int":"4"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"MEM"
          |                     },
          |                     {
          |                        "prim":"IF",
          |                        "args":[
          |                           [
          |                              {
          |                                 "prim":"PUSH",
          |                                 "args":[
          |                                    {
          |                                       "prim":"string"
          |                                    },
          |                                    {
          |                                       "string":"Archetype already registered"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"FAILWITH"
          |                              }
          |                           ],
          |                           [
          |
          |                           ]
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DIG",
          |                        "args":[
          |                           {
          |                              "int":"3"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DUP"
          |                     },
          |                     {
          |                        "prim":"DUG",
          |                        "args":[
          |                           {
          |                              "int":"4"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DIG",
          |                        "args":[
          |                           {
          |                              "int":"3"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DUP"
          |                     },
          |                     {
          |                        "prim":"DUG",
          |                        "args":[
          |                           {
          |                              "int":"4"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"MEM"
          |                     },
          |                     {
          |                        "prim":"IF",
          |                        "args":[
          |                           [
          |                              {
          |                                 "prim":"PUSH",
          |                                 "args":[
          |                                    {
          |                                       "prim":"string"
          |                                    },
          |                                    {
          |                                       "string":"KeyExists"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"FAILWITH"
          |                              }
          |                           ],
          |                           [
          |                              {
          |                                 "prim":"DIG",
          |                                 "args":[
          |                                    {
          |                                       "int":"3"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"DUP"
          |                              },
          |                              {
          |                                 "prim":"DUG",
          |                                 "args":[
          |                                    {
          |                                       "int":"4"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"DIG",
          |                                 "args":[
          |                                    {
          |                                       "int":"1"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"DUP"
          |                              },
          |                              {
          |                                 "prim":"DUG",
          |                                 "args":[
          |                                    {
          |                                       "int":"2"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"DIG",
          |                                 "args":[
          |                                    {
          |                                       "int":"3"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"DUP"
          |                              },
          |                              {
          |                                 "prim":"DUG",
          |                                 "args":[
          |                                    {
          |                                       "int":"4"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"PAIR"
          |                              },
          |                              {
          |                                 "prim":"SOME"
          |                              },
          |                              {
          |                                 "prim":"DIG",
          |                                 "args":[
          |                                    {
          |                                       "int":"4"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"DUP"
          |                              },
          |                              {
          |                                 "prim":"DUG",
          |                                 "args":[
          |                                    {
          |                                       "int":"5"
          |                                    }
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"UPDATE"
          |                              },
          |                              {
          |                                 "prim":"DIP",
          |                                 "args":[
          |                                    [
          |                                       {
          |                                          "prim":"DIG",
          |                                          "args":[
          |                                             {
          |                                                "int":"3"
          |                                             }
          |                                          ]
          |                                       },
          |                                       {
          |                                          "prim":"DROP"
          |                                       }
          |                                    ]
          |                                 ]
          |                              },
          |                              {
          |                                 "prim":"DUG",
          |                                 "args":[
          |                                    {
          |                                       "int":"3"
          |                                    }
          |                                 ]
          |                              }
          |                           ]
          |                        ]
          |                     },
          |                     {
          |                        "prim":"DROP",
          |                        "args":[
          |                           {
          |                              "int":"3"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"SWAP"
          |                     },
          |                     {
          |                        "prim":"PAIR"
          |                     },
          |                     {
          |                        "prim":"SWAP"
          |                     },
          |                     {
          |                        "prim":"PAIR"
          |                     },
          |                     {
          |                        "prim":"NIL",
          |                        "args":[
          |                           {
          |                              "prim":"operation"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"PAIR"
          |                     }
          |                  ]
          |               ]
          |            }
          |         ]
          |      ]
          |   },
          |   {
          |      "prim":"view",
          |      "args":[
          |         {
          |            "string":"containsArchId"
          |         },
          |         {
          |            "prim":"nat"
          |         },
          |         {
          |            "prim":"bool"
          |         },
          |         [
          |            [
          |               [
          |                  {
          |                     "prim":"DUP"
          |                  },
          |                  {
          |                     "prim":"CAR"
          |                  },
          |                  {
          |                     "prim":"DIP",
          |                     "args":[
          |                        [
          |                           {
          |                              "prim":"CDR"
          |                           }
          |                        ]
          |                     ]
          |                  }
          |               ]
          |            ],
          |            {
          |               "prim":"DIP",
          |               "args":[
          |                  [
          |                     {
          |                        "prim":"CDR"
          |                     },
          |                     {
          |                        "prim":"CDR"
          |                     }
          |                  ]
          |               ]
          |            },
          |            {
          |               "prim":"UNIT"
          |            },
          |            {
          |               "prim":"DIG",
          |               "args":[
          |                  {
          |                     "int":"2"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"DUP"
          |            },
          |            {
          |               "prim":"DUG",
          |               "args":[
          |                  {
          |                     "int":"3"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"DIG",
          |               "args":[
          |                  {
          |                     "int":"2"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"DUP"
          |            },
          |            {
          |               "prim":"DUG",
          |               "args":[
          |                  {
          |                     "int":"3"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"MEM"
          |            },
          |            {
          |               "prim":"SWAP"
          |            },
          |            {
          |               "prim":"DROP"
          |            },
          |            {
          |               "prim":"DIP",
          |               "args":[
          |                  [
          |                     {
          |                        "prim":"DROP",
          |                        "args":[
          |                           {
          |                              "int":"2"
          |                           }
          |                        ]
          |                     }
          |                  ]
          |               ]
          |            }
          |         ]
          |      ]
          |   },
          |   {
          |      "prim":"view",
          |      "args":[
          |         {
          |            "string":"getMaxBalance"
          |         },
          |         {
          |            "prim":"nat"
          |         },
          |         {
          |            "prim":"nat"
          |         },
          |         [
          |            [
          |               [
          |                  {
          |                     "prim":"DUP"
          |                  },
          |                  {
          |                     "prim":"CAR"
          |                  },
          |                  {
          |                     "prim":"DIP",
          |                     "args":[
          |                        [
          |                           {
          |                              "prim":"CDR"
          |                           }
          |                        ]
          |                     ]
          |                  }
          |               ]
          |            ],
          |            {
          |               "prim":"DIP",
          |               "args":[
          |                  [
          |                     {
          |                        "prim":"CDR"
          |                     },
          |                     {
          |                        "prim":"CDR"
          |                     }
          |                  ]
          |               ]
          |            },
          |            {
          |               "prim":"UNIT"
          |            },
          |            {
          |               "prim":"DIG",
          |               "args":[
          |                  {
          |                     "int":"2"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"DUP"
          |            },
          |            {
          |               "prim":"DUG",
          |               "args":[
          |                  {
          |                     "int":"3"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"DIG",
          |               "args":[
          |                  {
          |                     "int":"2"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"DUP"
          |            },
          |            {
          |               "prim":"DUG",
          |               "args":[
          |                  {
          |                     "int":"3"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"GET"
          |            },
          |            {
          |               "prim":"IF_NONE",
          |               "args":[
          |                  [
          |                     {
          |                        "prim":"PUSH",
          |                        "args":[
          |                           {
          |                              "prim":"string"
          |                           },
          |                           {
          |                              "string":"NotFound"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"FAILWITH"
          |                     }
          |                  ],
          |                  [
          |
          |                  ]
          |               ]
          |            },
          |            {
          |               "prim":"CDR"
          |            },
          |            {
          |               "prim":"SWAP"
          |            },
          |            {
          |               "prim":"DROP"
          |            },
          |            {
          |               "prim":"DIP",
          |               "args":[
          |                  [
          |                     {
          |                        "prim":"DROP",
          |                        "args":[
          |                           {
          |                              "int":"2"
          |                           }
          |                        ]
          |                     }
          |                  ]
          |               ]
          |            }
          |         ]
          |      ]
          |   },
          |   {
          |      "prim":"view",
          |      "args":[
          |         {
          |            "string":"getValidator"
          |         },
          |         {
          |            "prim":"nat"
          |         },
          |         {
          |            "prim":"address"
          |         },
          |         [
          |            [
          |               [
          |                  {
          |                     "prim":"DUP"
          |                  },
          |                  {
          |                     "prim":"CAR"
          |                  },
          |                  {
          |                     "prim":"DIP",
          |                     "args":[
          |                        [
          |                           {
          |                              "prim":"CDR"
          |                           }
          |                        ]
          |                     ]
          |                  }
          |               ]
          |            ],
          |            {
          |               "prim":"DIP",
          |               "args":[
          |                  [
          |                     {
          |                        "prim":"CDR"
          |                     },
          |                     {
          |                        "prim":"CDR"
          |                     }
          |                  ]
          |               ]
          |            },
          |            {
          |               "prim":"UNIT"
          |            },
          |            {
          |               "prim":"DIG",
          |               "args":[
          |                  {
          |                     "int":"2"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"DUP"
          |            },
          |            {
          |               "prim":"DUG",
          |               "args":[
          |                  {
          |                     "int":"3"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"DIG",
          |               "args":[
          |                  {
          |                     "int":"2"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"DUP"
          |            },
          |            {
          |               "prim":"DUG",
          |               "args":[
          |                  {
          |                     "int":"3"
          |                  }
          |               ]
          |            },
          |            {
          |               "prim":"GET"
          |            },
          |            {
          |               "prim":"IF_NONE",
          |               "args":[
          |                  [
          |                     {
          |                        "prim":"PUSH",
          |                        "args":[
          |                           {
          |                              "prim":"string"
          |                           },
          |                           {
          |                              "string":"NotFound"
          |                           }
          |                        ]
          |                     },
          |                     {
          |                        "prim":"FAILWITH"
          |                     }
          |                  ],
          |                  [
          |
          |                  ]
          |               ]
          |            },
          |            {
          |               "prim":"CAR"
          |            },
          |            {
          |               "prim":"SWAP"
          |            },
          |            {
          |               "prim":"DROP"
          |            },
          |            {
          |               "prim":"DIP",
          |               "args":[
          |                  [
          |                     {
          |                        "prim":"DROP",
          |                        "args":[
          |                           {
          |                              "int":"2"
          |                           }
          |                        ]
          |                     }
          |                  ]
          |               ]
          |            }
          |         ]
          |      ]
          |   }
          |]
          |""".stripMargin

      val result =
        """
          |parameter (or (address %setArchetype) (pair %add (nat %id) (pair %add (address %mintingValidator) (nat %imaxBalanceAllowed))));
          |   storage (pair (address %admin) (pair (option %archetype address) (big_map %archetypeLedger nat (pair (address %archValidator) (nat %maxBalanceAllowed)))));
          |   code { { { DUP ; CAR ; DIP { CDR } } } ;
          |          DIP { { { DUP ; CAR ; DIP { CDR } } } ; SWAP ; { { DUP ; CAR ; DIP { CDR } } } ; SWAP } ;
          |          IF_LEFT { DIG 3 ;
          |                    DUP ;
          |                    DUG 4 ;
          |                    SENDER ;
          |                    COMPARE ;
          |                    EQ ;
          |                    NOT ;
          |                    IF { PUSH string "InvalidCaller" ; FAILWITH } {} ;
          |                    DUP ;
          |                    SOME ;
          |                    DIP { DIG 2 ; DROP } ;
          |                    DUG 2 ;
          |                    DROP ;
          |                    SWAP ;
          |                    PAIR ;
          |                    SWAP ;
          |                    PAIR ;
          |                    NIL operation ;
          |                    PAIR }
          |                  { { { DUP ; CAR ; DIP { CDR } } } ;
          |                    SWAP ;
          |                    { { DUP ; CAR ; DIP { CDR } } } ;
          |                    SWAP ;
          |                    DIG 4 ;
          |                    DUP ;
          |                    DUG 5 ;
          |                    IF_NONE { PUSH string "NotFound" ; FAILWITH } {} ;
          |                    SENDER ;
          |                    COMPARE ;
          |                    EQ ;
          |                    NOT ;
          |                    IF { PUSH string "InvalidCaller" ; FAILWITH } {} ;
          |                    DIG 3 ;
          |                    DUP ;
          |                    DUG 4 ;
          |                    DIG 3 ;
          |                    DUP ;
          |                    DUG 4 ;
          |                    MEM ;
          |                    IF { PUSH string "Archetype already registered" ; FAILWITH } {} ;
          |                    DIG 3 ;
          |                    DUP ;
          |                    DUG 4 ;
          |                    DIG 3 ;
          |                    DUP ;
          |                    DUG 4 ;
          |                    MEM ;
          |                    IF { PUSH string "KeyExists" ;
          |                         FAILWITH }
          |                       { DIG 3 ;
          |                         DUP ;
          |                         DUG 4 ;
          |                         DIG 1 ;
          |                         DUP ;
          |                         DUG 2 ;
          |                         DIG 3 ;
          |                         DUP ;
          |                         DUG 4 ;
          |                         PAIR ;
          |                         SOME ;
          |                         DIG 4 ;
          |                         DUP ;
          |                         DUG 5 ;
          |                         UPDATE ;
          |                         DIP { DIG 3 ; DROP } ;
          |                         DUG 3 } ;
          |                    DROP 3 ;
          |                    SWAP ;
          |                    PAIR ;
          |                    SWAP ;
          |                    PAIR ;
          |                    NIL operation ;
          |                    PAIR } }
          |   views { { "containsArchId" ; nat ; bool ; { { { DUP ; CAR ; DIP { CDR } } } ; DIP { CDR ; CDR } ; UNIT ; DIG 2 ; DUP ; DUG 3 ; DIG 2 ; DUP ; DUG 3 ; MEM ; SWAP ; DROP ; DIP { DROP 2 } } } ;
          |   { "getMaxBalance" ; nat ; nat ; { { { DUP ; CAR ; DIP { CDR } } } ; DIP { CDR ; CDR } ; UNIT ; DIG 2 ; DUP ; DUG 3 ; DIG 2 ; DUP ; DUG 3 ; GET ; IF_NONE { PUSH string "NotFound" ; FAILWITH } {} ; CDR ; SWAP ; DROP ; DIP { DROP 2 } } } ;
          |   { "getValidator" ; nat ; address ; { { { DUP ; CAR ; DIP { CDR } } } ; DIP { CDR ; CDR } ; UNIT ; DIG 2 ; DUP ; DUG 3 ; DIG 2 ; DUP ; DUG 3 ; GET ; IF_NONE { PUSH string "NotFound" ; FAILWITH } {} ; CAR ; SWAP ; DROP ; DIP { DROP 2 } } } }
          |""".stripMargin

      JsonToMichelson.convert[MichelsonSchema](json).map(_.filterNot(_.isWhitespace)) should equal(Right(result.filterNot(_.isWhitespace)))
    }

}
