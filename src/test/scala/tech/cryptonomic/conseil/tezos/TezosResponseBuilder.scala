package tech.cryptonomic.conseil.tezos

object TezosResponseBuilder {

  val votesPeriodKind = """ "proposal" """.trim
  val votesQuorum = "1000"
  val votesProposal = "null"

  val genesisBlockResponse: String =
    """{
      "chain_id": "NetXgtSLGNJvNye",
      "hash": "BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe",
      "header": {
          "context": "CoVfFHwx43W4TR88ZDuGGHdp5HP2Ak83m2nQKftSTc8AjmXdUV4b",
          "fitness": [],
          "level": 0,
          "operations_hash": "LLoZS2LW3rEi7KYU4ouBQtorua37aWWCtpDmv1n2x3xoKi6sVXLWp",
          "predecessor": "BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe",
          "proto": 0,
          "timestamp": "2018-11-30T15:30:56Z",
          "validation_pass": 0
      },
      "metadata": {
          "max_block_header_length": 105,
          "max_operation_data_length": 0,
          "max_operation_list_length": [],
          "max_operations_ttl": 0,
          "next_protocol": "Ps6mwMrF2ER2s51cp9yYpjDcuzQjsc2yAz8bQsRgdaRxw4Fk95H",
          "protocol": "PrihK96nBAFSxVL1GLJTVhu9YnzkMFiBeuJRPA8NwuZVZCE1L6i",
          "test_chain_status": {
              "status": "not_running"
          }
      },
      "operations": [],
      "protocol": "PrihK96nBAFSxVL1GLJTVhu9YnzkMFiBeuJRPA8NwuZVZCE1L6i"
    }"""

    val blockResponse: String =
    """{
      "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
      "chain_id": "NetXSzLHKwSumh7",
      "hash": "BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c",
      "header": {
        "level": 3,
        "proto": 1,
        "predecessor": "BKnYkTEdwfDAYitjVM6s7AL5wNSUGeYVuUkKpnF1k9xijPwmUav",
        "timestamp": "2019-01-04T10:37:53Z",
        "validation_pass": 4,
        "operations_hash": "LLoZVH6MTMS97DPYMRgHaQ5LoJ6zUS6HNuPtct7SyY8yx77cyndHt",
        "fitness": [
        "00",
        "0000000000508637"
        ],
        "context": "CoVPTgn2Dyce8F1Js2iQqJdj4BLnfMuktzAyRocdJ3Vq66bQZw7z",
        "priority": 0,
        "proof_of_work_nonce": "a983c23d0e5233bf",
        "signature": "sigPsHz3864TiJBAMoeZEw2p26Aq9LfYQ4SbFZkkLhYe3k4mStXsHhkCV5wfSc7fGMXdpyLSZjsTdkiUWXrBc1u1T2JSYab9"
      },
      "metadata": {
        "baker": "tz1YCABRTa6H8PLKx2EtDWeCGPaKxUhNgv47",
        "balance_updates": []
      }
    }"""

  val operationsResponse: String =
    """[
         [
           {
             "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
             "chain_id": "NetXSzLHKwSumh7",
             "hash": "opUu4Pbq1mDeikCS72edJJf49mLP36pXeMFxJf1RCNB9RS4sj7n",
             "branch": "BKqSNXNSgb52KUaArhqQ7khHZGfkeuKyuTBhXrktaXmaFEniZ96",
             "contents": [
               {
                 "kind": "endorsement",
                 "level": 3,
                 "metadata": {
                   "balance_updates": [
                     {
                       "kind": "contract",
                       "contract": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                       "change": "-320000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "deposits",
                       "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                       "level": 1266,
                       "change": "320000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "rewards",
                       "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                       "level": 1266,
                       "change": "2500000"
                     }
                   ],
                   "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                   "slots": [
                     31,
                     28,
                     24,
                     20,
                     17
                   ]
                 }
               }
             ],
             "signature": "sigp2GMJ8H2iB3ywBny6Wpsy8cDctfiLC12D6Rc43AsnhKAApReobiy3JeFQLwjiXdNju3wRd9qC59yLvoQpbTBagNLRmSiP"
           },
           {
             "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
             "chain_id": "NetXSzLHKwSumh7",
             "hash": "oouwqHdb9ejfwDoLmJqvR7G4HG4vbQqHFuxcVgi7XAsesjycfp8",
             "branch": "BKqSNXNSgb52KUaArhqQ7khHZGfkeuKyuTBhXrktaXmaFEniZ96",
             "contents": [
               {
                 "kind": "endorsement",
                 "level": 3,
                 "metadata": {
                   "balance_updates": [
                     {
                       "kind": "contract",
                       "contract": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                       "change": "-576000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "deposits",
                       "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                       "level": 1266,
                       "change": "576000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "rewards",
                       "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                       "level": 1266,
                       "change": "4500000"
                     }
                   ],
                   "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                   "slots": [
                     30,
                     29,
                     26,
                     23,
                     19,
                     16,
                     9,
                     5,
                     2
                   ]
                 }
               }
             ],
             "signature": "sigTkxpK3VDDAo3Lr58GcsWaegqcboFBRTVGzJVLFztCV3tQSEFQLAePU4f3yxiRgN5kCcPooNnu2xiMVweq6peELNJuYf6i"
           },
           {
             "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
             "chain_id": "NetXSzLHKwSumh7",
             "hash": "onryDh3VYQ5nfvx9CbiBJHUrnZLjVE15997ZqWhLMKzew6z36Nb",
             "branch": "BKqSNXNSgb52KUaArhqQ7khHZGfkeuKyuTBhXrktaXmaFEniZ96",
             "contents": [
               {
                 "kind": "endorsement",
                 "level": 3,
                 "metadata": {
                   "balance_updates": [
                     {
                       "kind": "contract",
                       "contract": "tz1dQ3WQpKTu1Vi3ZF6DwaZRgdEVS28RNw2h",
                       "change": "-64000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "deposits",
                       "delegate": "tz1dQ3WQpKTu1Vi3ZF6DwaZRgdEVS28RNw2h",
                       "level": 1266,
                       "change": "64000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "rewards",
                       "delegate": "tz1dQ3WQpKTu1Vi3ZF6DwaZRgdEVS28RNw2h",
                       "level": 1266,
                       "change": "500000"
                     }
                   ],
                   "delegate": "tz1dQ3WQpKTu1Vi3ZF6DwaZRgdEVS28RNw2h",
                   "slots": [
                     13
                   ]
                 }
               }
             ],
             "signature": "sigsykyZzMwH1J8k7JndyKcyZ4GqvjeKHhHvo1PTK9Ack3sThjT4B2xUiRC4SfQvc7DpgEzSUL1x4nXUSwxmMUF3EJUmUttv"
           },
           {
             "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
             "chain_id": "NetXSzLHKwSumh7",
             "hash": "opEsaKeVJG2dTAUoBfCLC59h11EhyRsdDneD6cJrVB1QarWM4N5",
             "branch": "BKqSNXNSgb52KUaArhqQ7khHZGfkeuKyuTBhXrktaXmaFEniZ96",
             "contents": [
               {
                 "kind": "endorsement",
                 "level": 3,
                 "metadata": {
                   "balance_updates": [
                     {
                       "kind": "contract",
                       "contract": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                       "change": "-192000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "deposits",
                       "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                       "level": 1266,
                       "change": "192000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "rewards",
                       "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                       "level": 1266,
                       "change": "1500000"
                     }
                   ],
                   "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                   "slots": [
                     25,
                     11,
                     8
                   ]
                 }
               }
             ],
             "signature": "sigpppRLbjny67MAzpzaeS7VQRtfjVcD9a9RAtftBeVha4LTFzMwhasQAZvUdMxSUJBy9j22NyyE5rNQjQueHzy4ESjS8aC5"
           },
           {
             "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
             "chain_id": "NetXSzLHKwSumh7",
             "hash": "onyrZ2BPu4ZouDgrjoCvrpKCV69hCftPkHK3BB84b67aQnj5ruJ",
             "branch": "BKqSNXNSgb52KUaArhqQ7khHZGfkeuKyuTBhXrktaXmaFEniZ96",
             "contents": [
               {
                 "kind": "endorsement",
                 "level": 3,
                 "metadata": {
                   "balance_updates": [
                     {
                       "kind": "contract",
                       "contract": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                       "change": "-192000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "deposits",
                       "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                       "level": 1266,
                       "change": "192000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "rewards",
                       "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                       "level": 1266,
                       "change": "1500000"
                     }
                   ],
                   "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                   "slots": [
                     27,
                     14,
                     3
                   ]
                 }
               }
             ],
             "signature": "sigpYuS7mLRYqsBE9A61ZQNi3pMWLDJiZP4tYHJxXqez2Dnk8Z1nPyZz8GwPwEiAZ7DDgyfTucD9oEMDYDNf5VbJT2JGhPgq"
           },
           {
             "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
             "chain_id": "NetXSzLHKwSumh7",
             "hash": "onzHBWYVExtb2yZy72grdS94RUELzBEMHjE39qdErdxYs5aqih1",
             "branch": "BKqSNXNSgb52KUaArhqQ7khHZGfkeuKyuTBhXrktaXmaFEniZ96",
             "contents": [
               {
                 "kind": "endorsement",
                 "level": 3,
                 "metadata": {
                   "balance_updates": [
                     {
                       "kind": "contract",
                       "contract": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                       "change": "-576000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "deposits",
                       "delegate": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                       "level": 1266,
                       "change": "576000000"
                     },
                     {
                       "kind": "freezer",
                       "category": "rewards",
                       "delegate": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                       "level": 1266,
                       "change": "4500000"
                     }
                   ],
                   "delegate": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                   "slots": [
                     22,
                     21,
                     18,
                     15,
                     12,
                     10,
                     7,
                     4,
                     1
                   ]
                 }
               }
             ],
             "signature": "sigY8LYCqh5V3A1RxcGyWXrYSSx5zZytukQewjiVktyx9ArXr6GUhoQ49TknGr5QzMBwSpZsmx7YhVBv6tZSFb6WQe6BUExX"
           }
         ]
       ]"""

  val batchedGetBlockQueryResponse: String =
    """{
        "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
        "chain_id": "NetXSzLHKwSumh7",
        "hash": "BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF",
        "header": {
          "level": 162385,
          "proto": 1,
          "predecessor": "BLpMRZAb9rAdpmqRvP8KJJtsQhprMKFCmKfpXAJFmuUwqikcttq",
          "timestamp": "2019-01-04T12:39:13Z",
          "validation_pass": 4,
          "operations_hash": "LLob1ApXRrxVi1UA23QsxhWbUkMgfioVE7ofEBUwnWH9MiR1sRGJu",
          "fitness": [
            "00",
            "000000000050aa32"
          ],
          "context": "CoVeQxZyVtRQ2PAX6SWtz9RL1WyooGDrhYWTaKzD8otj1Z9c6W5g",
          "priority": 0,
          "proof_of_work_nonce": "5d640a7987d09971",
          "signature": "sigu3mpnD22SGtiY3R6bKB1DbcUQJWJisnvf47EcW32uZqznhCD5iQC9d223ELUX1H79MdgrECbMN52djXEjBek2uhqHpLoj"
        },
        "metadata": {
          "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
          "next_protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
          "test_chain_status": {
            "status": "not_running"
          },
          "max_operations_ttl": 60,
          "max_operation_data_length": 16384,
          "max_block_header_length": 238,
          "max_operation_list_length": [
            {
              "max_size": 32768,
              "max_op": 32
            },
            {
              "max_size": 32768
            },
            {
              "max_size": 135168,
              "max_op": 132
            },
            {
              "max_size": 524288
            }
          ],
          "baker": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
          "level": {
            "level": 162385,
            "level_position": 162384,
            "cycle": 1268,
            "cycle_position": 80,
            "voting_period": 4,
            "voting_period_position": 31312,
            "expected_commitment": false
          },
          "voting_period_kind": "proposal",
          "nonce_hash": null,
          "consumed_gas": "0",
          "deactivated": [],
          "balance_updates": [
            {
              "kind": "contract",
              "contract": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
              "change": "-512000000"
            },
            {
              "kind": "freezer",
              "category": "deposits",
              "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
              "level": 1268,
              "change": "512000000"
            },
            {
              "kind": "freezer",
              "category": "rewards",
              "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
              "level": 1268,
              "change": "16000000"
            }
          ]
        },
        "operations": [
          [
            {
              "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
              "chain_id": "NetXSzLHKwSumh7",
              "hash": "opGsA5UVQDbt3iibN2QBceZvWiMho7MyAqa7r8cEnCRxwAKWov1",
              "branch": "BLpMRZAb9rAdpmqRvP8KJJtsQhprMKFCmKfpXAJFmuUwqikcttq",
              "contents": [
                {
                  "kind": "endorsement",
                  "level": 162384,
                  "metadata": {
                    "balance_updates": [
                      {
                        "kind": "contract",
                        "contract": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                        "change": "-704000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "deposits",
                        "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                        "level": 1268,
                        "change": "704000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "rewards",
                        "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                        "level": 1268,
                        "change": "22000000"
                      }
                    ],
                    "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                    "slots": [
                      31,
                      30,
                      27,
                      26,
                      25,
                      24,
                      21,
                      14,
                      13,
                      8,
                      0
                    ]
                  }
                }
              ],
              "signature": "sigjpLmxnMcXtya32UnVaezggKKB9xzqAQgSaj616nJHu3fk4Cxeiqt8DfitwumaPbE6qAh2mhPnx9VJkB6cB6iiED3TZ7kE"
            },
            {
              "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
              "chain_id": "NetXSzLHKwSumh7",
              "hash": "opHetLWQE3ECcSfiHozQCKBBbUAnCHHTo8FFG9u5WrNRxQG4Azz",
              "branch": "BLpMRZAb9rAdpmqRvP8KJJtsQhprMKFCmKfpXAJFmuUwqikcttq",
              "contents": [
                {
                  "kind": "endorsement",
                  "level": 162384,
                  "metadata": {
                    "balance_updates": [
                      {
                        "kind": "contract",
                        "contract": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                        "change": "-320000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "deposits",
                        "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                        "level": 1268,
                        "change": "320000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "rewards",
                        "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                        "level": 1268,
                        "change": "10000000"
                      }
                    ],
                    "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                    "slots": [
                      29,
                      20,
                      18,
                      17,
                      12
                    ]
                  }
                }
              ],
              "signature": "sigiKLEn11cqzo89KxKK1z51BRzvHgLDFpHTdyhvqMCN7DAAJA6Xa2jDEy4yYAo71vChx5A8NjiCgVVG5JeaizegtsqJcDQ9"
            },
            {
              "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
              "chain_id": "NetXSzLHKwSumh7",
              "hash": "opCaQ1EajGXRyy8sBjsK2FHJm7KUJ8T8XEbkSuVTSdMCTs5QZqR",
              "branch": "BLpMRZAb9rAdpmqRvP8KJJtsQhprMKFCmKfpXAJFmuUwqikcttq",
              "contents": [
                {
                  "kind": "endorsement",
                  "level": 162384,
                  "metadata": {
                    "balance_updates": [
                      {
                        "kind": "contract",
                        "contract": "tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ",
                        "change": "-64000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "deposits",
                        "delegate": "tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ",
                        "level": 1268,
                        "change": "64000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "rewards",
                        "delegate": "tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ",
                        "level": 1268,
                        "change": "2000000"
                      }
                    ],
                    "delegate": "tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ",
                    "slots": [
                      19
                    ]
                  }
                }
              ],
              "signature": "sigPwB1V3M4ezWNHgwDs7m8nxApSfSCPe2qwWijYboVzNESTD9pDfXK97TwdKQDzcc9cFwrDmu7zJKHvMZ9sJpRCyeZDCxFb"
            },
            {
              "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
              "chain_id": "NetXSzLHKwSumh7",
              "hash": "opDGRqHJhAQWYqpyc2RVJWb8z2u1pPETM8AqwRRamgQzu2g7sU2",
              "branch": "BLpMRZAb9rAdpmqRvP8KJJtsQhprMKFCmKfpXAJFmuUwqikcttq",
              "contents": [
                {
                  "kind": "endorsement",
                  "level": 162384,
                  "metadata": {
                    "balance_updates": [
                      {
                        "kind": "contract",
                        "contract": "tz1SkbPvfcqUXbs3ywBkAKFDLGvjQawLXEKZ",
                        "change": "-128000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "deposits",
                        "delegate": "tz1SkbPvfcqUXbs3ywBkAKFDLGvjQawLXEKZ",
                        "level": 1268,
                        "change": "128000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "rewards",
                        "delegate": "tz1SkbPvfcqUXbs3ywBkAKFDLGvjQawLXEKZ",
                        "level": 1268,
                        "change": "4000000"
                      }
                    ],
                    "delegate": "tz1SkbPvfcqUXbs3ywBkAKFDLGvjQawLXEKZ",
                    "slots": [
                      11,
                      6
                    ]
                  }
                }
              ],
              "signature": "siggdASz2uwaYxo7ahvpcWsfEqGhQpyCZHEK3PwW2KME5YXEye7PbmMtrWgGeZip238aTdVBruaZw4HEJdkDiyoDDrGzx4QY"
            },
            {
              "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
              "chain_id": "NetXSzLHKwSumh7",
              "hash": "opUwgH6rDpNKt6rMKTPpDGdTKwu79cSviR5pzuUmMkMBG6RLZKh",
              "branch": "BLpMRZAb9rAdpmqRvP8KJJtsQhprMKFCmKfpXAJFmuUwqikcttq",
              "contents": [
                {
                  "kind": "endorsement",
                  "level": 162384,
                  "metadata": {
                    "balance_updates": [
                      {
                        "kind": "contract",
                        "contract": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                        "change": "-448000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "deposits",
                        "delegate": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                        "level": 1268,
                        "change": "448000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "rewards",
                        "delegate": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                        "level": 1268,
                        "change": "14000000"
                      }
                    ],
                    "delegate": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                    "slots": [
                      22,
                      15,
                      9,
                      7,
                      5,
                      4,
                      2
                    ]
                  }
                }
              ],
              "signature": "sigXSqjiAw7FqEFTUnnjNCY1nvWrymjFd3HAeCfFHHxuVdu5St7ZkT6VQ4LcZrGXt62mjs4e2rxhY34xQnAEEVmWVVZJTg86"
            },
            {
              "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
              "chain_id": "NetXSzLHKwSumh7",
              "hash": "oomeN2j1mrDfEMhCgwUEcjJ5krQxajJiYRW6VZA9RE6sBk4RnwS",
              "branch": "BLpMRZAb9rAdpmqRvP8KJJtsQhprMKFCmKfpXAJFmuUwqikcttq",
              "contents": [
                {
                  "kind": "endorsement",
                  "level": 162384,
                  "metadata": {
                    "balance_updates": [
                      {
                        "kind": "contract",
                        "contract": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                        "change": "-128000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "deposits",
                        "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                        "level": 1268,
                        "change": "128000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "rewards",
                        "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                        "level": 1268,
                        "change": "4000000"
                      }
                    ],
                    "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                    "slots": [
                      10,
                      3
                    ]
                  }
                }
              ],
              "signature": "sigfid4MjfBmUpEiiaL21J2s86wb9rBKRry915AfhNcwcEHPcXv6MmR46c7FYcvsAxdNeknRJMfMZrU88XuvHwmG9auzjFpj"
            },
            {
              "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
              "chain_id": "NetXSzLHKwSumh7",
              "hash": "onumhkSL7vsdwiTKeSpGwbL9cCRyL4ghPfp9Pc17vq38YCyNM9W",
              "branch": "BLpMRZAb9rAdpmqRvP8KJJtsQhprMKFCmKfpXAJFmuUwqikcttq",
              "contents": [
                {
                  "kind": "endorsement",
                  "level": 162384,
                  "metadata": {
                    "balance_updates": [
                      {
                        "kind": "contract",
                        "contract": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                        "change": "-192000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "deposits",
                        "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                        "level": 1268,
                        "change": "192000000"
                      },
                      {
                        "kind": "freezer",
                        "category": "rewards",
                        "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                        "level": 1268,
                        "change": "6000000"
                      }
                    ],
                    "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                    "slots": [
                      28,
                      23,
                      16
                    ]
                  }
                }
              ],
              "signature": "sigrEytfSFTB5hWkf3imTMhJdBKZ4PYqY3yRnTfG8LMi38zGPoMxEV3mjFUtNuygVnKGoXUEGhAiUFBh8H6X18oUGqmcG1Ce"
            }
          ]
        ]
      }"""

  val batchedGetOperationsQueryResponse: String =
    """[
        [
          {
            "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
            "chain_id": "NetXSzLHKwSumh7",
            "hash": "oogBgjU5bABoptgQyzK5H3B7qyYq18DqLEKJenz9XKaP6RN5t9P",
            "branch": "BLezRUSVahKD6RL8BVJSJDdfzwma2fCzwkgYQKvHr4pskHS6pNA",
            "contents": [
              {
                "kind": "endorsement",
                "level": 162489,
                "metadata": {
                  "balance_updates": [
                    {
                      "kind": "contract",
                      "contract": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                      "change": "-320000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "deposits",
                      "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                      "level": 1269,
                      "change": "320000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "rewards",
                      "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                      "level": 1269,
                      "change": "10000000"
                    }
                  ],
                  "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
                  "slots": [
                    30,
                    24,
                    14,
                    12,
                    11
                  ]
                }
              }
            ],
            "signature": "sigQjHHZBqBS963CMsYPF8FzvAKmPgwNrYMW96zsVZuTjFFw62vKWpsukL4obbSz5azRECaqmwctRr3cTsHBBUzCfmjxTX7g"
          },
          {
            "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
            "chain_id": "NetXSzLHKwSumh7",
            "hash": "oosyyoNHc4mnNUU58PSoTBgBqCwG3zUHQGPagn3e1Ac7qnLewmn",
            "branch": "BLezRUSVahKD6RL8BVJSJDdfzwma2fCzwkgYQKvHr4pskHS6pNA",
            "contents": [
              {
                "kind": "endorsement",
                "level": 162489,
                "metadata": {
                  "balance_updates": [
                    {
                      "kind": "contract",
                      "contract": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                      "change": "-704000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "deposits",
                      "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                      "level": 1269,
                      "change": "704000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "rewards",
                      "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                      "level": 1269,
                      "change": "22000000"
                    }
                  ],
                  "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
                  "slots": [
                    29,
                    28,
                    22,
                    20,
                    17,
                    13,
                    9,
                    3,
                    2,
                    1,
                    0
                  ]
                }
              }
            ],
            "signature": "sigQ93fS4Be1ZSJaK1xDxi2jpURQcp1C5tWSYE9NvRdYhjtpCtZnq6ABChMLF47rkmGDREoCPjYebnYvQuXnTPb8t6svbu3s"
          },
          {
            "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
            "chain_id": "NetXSzLHKwSumh7",
            "hash": "onsZLK2CHeQ7LeMzYjRZ6BE5kCAYKazPjaoExwMCcsVbxzTe2Nj",
            "branch": "BLezRUSVahKD6RL8BVJSJDdfzwma2fCzwkgYQKvHr4pskHS6pNA",
            "contents": [
              {
                "kind": "endorsement",
                "level": 162489,
                "metadata": {
                  "balance_updates": [
                    {
                      "kind": "contract",
                      "contract": "tz1dQ3WQpKTu1Vi3ZF6DwaZRgdEVS28RNw2h",
                      "change": "-64000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "deposits",
                      "delegate": "tz1dQ3WQpKTu1Vi3ZF6DwaZRgdEVS28RNw2h",
                      "level": 1269,
                      "change": "64000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "rewards",
                      "delegate": "tz1dQ3WQpKTu1Vi3ZF6DwaZRgdEVS28RNw2h",
                      "level": 1269,
                      "change": "2000000"
                    }
                  ],
                  "delegate": "tz1dQ3WQpKTu1Vi3ZF6DwaZRgdEVS28RNw2h",
                  "slots": [
                    31
                  ]
                }
              }
            ],
            "signature": "sigoQvU2Vz2xhi5iQChcyi7WxkD5eLbZgdpMaCSE692qnypxdCYWR1uVYY5mqtJkH8hThuXGj3HNorVD5bVMykgeh1Y3yt7y"
          },
          {
            "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
            "chain_id": "NetXSzLHKwSumh7",
            "hash": "ooXFKyFQ3kUXXe6PGDGH6ej5j6Ud2bQ6PT2hPwMDQF1z6j1SLWz",
            "branch": "BLezRUSVahKD6RL8BVJSJDdfzwma2fCzwkgYQKvHr4pskHS6pNA",
            "contents": [
              {
                "kind": "endorsement",
                "level": 162489,
                "metadata": {
                  "balance_updates": [
                    {
                      "kind": "contract",
                      "contract": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                      "change": "-64000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "deposits",
                      "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                      "level": 1269,
                      "change": "64000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "rewards",
                      "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                      "level": 1269,
                      "change": "2000000"
                    }
                  ],
                  "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
                  "slots": [
                    18
                  ]
                }
              }
            ],
            "signature": "sigsbWdKEsjdJEERWfY3iYdbduc27ZN4cBwuwKFBfS4LEq1iPnUmNYzzFHu3oyKmMFWp5K5aLJy3PrYjznk3DXSLcc624XMH"
          },
          {
            "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
            "chain_id": "NetXSzLHKwSumh7",
            "hash": "opLSypLnDwDwveSx6c71yMJhrttN9MAtZdbmkfmfwKxagJQbiGc",
            "branch": "BLezRUSVahKD6RL8BVJSJDdfzwma2fCzwkgYQKvHr4pskHS6pNA",
            "contents": [
              {
                "kind": "endorsement",
                "level": 162489,
                "metadata": {
                  "balance_updates": [
                    {
                      "kind": "contract",
                      "contract": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                      "change": "-384000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "deposits",
                      "delegate": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                      "level": 1269,
                      "change": "384000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "rewards",
                      "delegate": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                      "level": 1269,
                      "change": "12000000"
                    }
                  ],
                  "delegate": "tz3Q67aMz7gSMiQRcW729sXSfuMtkyAHYfqc",
                  "slots": [
                    27,
                    26,
                    23,
                    8,
                    6,
                    4
                  ]
                }
              }
            ],
            "signature": "sign29wtn2wc8qj9ZLv8u3t2s27bLB5CWxTsTLsf7c7M2VaeDYe9SmCdmKSjsy1gaGgMBBtuXjaXS2NJi3SmEAmWGnhYjpj2"
          },
          {
            "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
            "chain_id": "NetXSzLHKwSumh7",
            "hash": "opBdH2vRrBm9gX3fjGzMKnDJX3TcmR5E4GSgTmQ5YFdV3gvXDUM",
            "branch": "BLezRUSVahKD6RL8BVJSJDdfzwma2fCzwkgYQKvHr4pskHS6pNA",
            "contents": [
              {
                "kind": "endorsement",
                "level": 162489,
                "metadata": {
                  "balance_updates": [
                    {
                      "kind": "contract",
                      "contract": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                      "change": "-192000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "deposits",
                      "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                      "level": 1269,
                      "change": "192000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "rewards",
                      "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                      "level": 1269,
                      "change": "6000000"
                    }
                  ],
                  "delegate": "tz1UKmZhi8dhUX5a5QTfCrsH9pK4dt1dVfJo",
                  "slots": [
                    19,
                    7,
                    5
                  ]
                }
              }
            ],
            "signature": "sigViNptDQLxHNy6sZu1gK45tDV5traAHfZdeD5Lw1VjyMSR5MoqGnP1qRKN3Q7YyMZzRmEAk2zTKR9kWkfirdZwaZw7n7Mo"
          },
          {
            "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
            "chain_id": "NetXSzLHKwSumh7",
            "hash": "oopzEArX65hX2K6dEASHjksF24XynUp28wzLHLpx24WGG3vEQK1",
            "branch": "BLezRUSVahKD6RL8BVJSJDdfzwma2fCzwkgYQKvHr4pskHS6pNA",
            "contents": [
              {
                "kind": "endorsement",
                "level": 162489,
                "metadata": {
                  "balance_updates": [
                    {
                      "kind": "contract",
                      "contract": "tz1SkbPvfcqUXbs3ywBkAKFDLGvjQawLXEKZ",
                      "change": "-320000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "deposits",
                      "delegate": "tz1SkbPvfcqUXbs3ywBkAKFDLGvjQawLXEKZ",
                      "level": 1269,
                      "change": "320000000"
                    },
                    {
                      "kind": "freezer",
                      "category": "rewards",
                      "delegate": "tz1SkbPvfcqUXbs3ywBkAKFDLGvjQawLXEKZ",
                      "level": 1269,
                      "change": "10000000"
                    }
                  ],
                  "delegate": "tz1SkbPvfcqUXbs3ywBkAKFDLGvjQawLXEKZ",
                  "slots": [
                    25,
                    21,
                    16,
                    15,
                    10
                  ]
                }
              }
            ],
            "signature": "sigXfXaGWFTtNZgDUEhE3Q86Bv2VqXzPRRXYaKKyLcGbLqrpWktxXBrCcRbiJfxJpTYGR14X5jciG7DiNTau6bYsVtdqDTPn"
          }
        ]
      ]"""

}
