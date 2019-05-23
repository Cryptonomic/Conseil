package tech.cryptonomic.conseil.tezos

object TezosResponseBuilder {

  val votesQuorum = 1000

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

  /* Will return premade responses at different indexes, where we can
   * assume the index corresponds to an offset, from 0 to 3
   */
  val precannedGetBlockQueryResponse: Array[String] = Array(
    """{
      "protocol": "PsddFKi32cMJ2qPjf43Qv5GDWLDPZb3T3bF6fLKiF5HtvHNU7aP",
      "chain_id": "NetXgtSLGNJvNye",
      "hash": "BLTQ5B4T4Tyzqfm3Yfwi26WmdQScr6UXVSE9du6N71LYjgSwbtc",
      "header": {
          "level": 3,
          "proto": 1,
          "predecessor": "BLwKksYwrxt39exDei7yi47h7aMcVY2kZMZhTwEEoSUwToQUiDV",
          "timestamp": "2018-11-30T18:17:58Z",
          "validation_pass": 4,
          "operations_hash": "LLob2GYcoXRQp9Ps6tSsFE5Fop8ZpT1WmsXAA1eLSUkaQiySqj2kj",
          "fitness": [
              "00",
              "0000000000000005"
          ],
          "context": "CoWUBkx1FjpHaErKcr6ip5ofu1Q9crb9E9V4KWjAf1tuRNsJeVyM",
          "priority": 12,
          "proof_of_work_nonce": "00000003e5445371",
          "signature": "sigTh4ikMsW1ZXUXp3HbfSfV9mi6rKo8xoGHi1bmM9krBbrJcaB5fQFKCDFakpSPkGB5BAZEB7dEH4rPe3oyzDX4TL2NroUh"
      },
      "metadata": {
          "protocol": "PsddFKi32cMJ2qPjf43Qv5GDWLDPZb3T3bF6fLKiF5HtvHNU7aP",
          "next_protocol": "PsddFKi32cMJ2qPjf43Qv5GDWLDPZb3T3bF6fLKiF5HtvHNU7aP",
          "test_chain_status": {
              "status": "not_running"
          },
          "max_operations_ttl": 3,
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
          "baker": "tz1YCABRTa6H8PLKx2EtDWeCGPaKxUhNgv47",
          "level": {
              "level": 3,
              "level_position": 2,
              "cycle": 0,
              "cycle_position": 2,
              "voting_period": 0,
              "voting_period_position": 2,
              "expected_commitment": false
          },
          "voting_period_kind": "proposal",
          "nonce_hash": null,
          "consumed_gas": "0",
          "deactivated": [],
          "balance_updates": []
      },
      "operations": [
          [
              {
                  "protocol": "PsddFKi32cMJ2qPjf43Qv5GDWLDPZb3T3bF6fLKiF5HtvHNU7aP",
                  "chain_id": "NetXgtSLGNJvNye",
                  "hash": "oobuLSAcTvomrF2dFPCp5mLxABSD8o65v6wL79ihJY75dkUkhdd",
                  "branch": "BLwKksYwrxt39exDei7yi47h7aMcVY2kZMZhTwEEoSUwToQUiDV",
                  "contents": [
                      {
                          "kind": "endorsement",
                          "level": 2,
                          "metadata": {
                              "balance_updates": [],
                              "delegate": "tz1YCABRTa6H8PLKx2EtDWeCGPaKxUhNgv47",
                              "slots": [
                                  29,
                                  10
                              ]
                          }
                      }
                  ],
                  "signature": "sigV75bAaiVgc2sNT9u9KLQaBLtdXLiQDq7wJwNhvhPPoCZwoWZXhzCAHKgaZtkAGBthmkYswDXkarkf5Fa3jc9GrV2kvHtN"
              }
          ],
          [],
          [],
          []
      ]
    }""",
    """{
      "protocol": "PsddFKi32cMJ2qPjf43Qv5GDWLDPZb3T3bF6fLKiF5HtvHNU7aP",
      "chain_id": "NetXgtSLGNJvNye",
      "hash": "BLwKksYwrxt39exDei7yi47h7aMcVY2kZMZhTwEEoSUwToQUiDV",
      "header": {
          "level": 2,
          "proto": 1,
          "predecessor": "BMPtRJqFGQJRTfn8bXQR2grLE1M97XnUmG5vgjHMW7St1Wub7Cd",
          "timestamp": "2018-11-30T18:09:28Z",
          "validation_pass": 4,
          "operations_hash": "LLoa7bxRTKaQN2bLYoitYB6bU2DvLnBAqrVjZcvJ364cTcX2PZYKU",
          "fitness": [
              "00",
              "0000000000000002"
          ],
          "context": "CoVpwkeM7FoDnWvLnyJ4h4cjjj1NTE3VkSY3DsdVkCLZ7FAMeJ3N",
          "priority": 5,
          "proof_of_work_nonce": "00000003ba671eef",
          "signature": "sigr593uq6VSoHSGKbsya4ezDzT4qTikk4iVuNwQUA4ZHmXYT3HTHqDatWvBxmDUKPnCXgFVSDS93hZpPUQWiWPEBrEw5W9F"
      },
      "metadata": {
          "protocol": "PsddFKi32cMJ2qPjf43Qv5GDWLDPZb3T3bF6fLKiF5HtvHNU7aP",
          "next_protocol": "PsddFKi32cMJ2qPjf43Qv5GDWLDPZb3T3bF6fLKiF5HtvHNU7aP",
          "test_chain_status": {
              "status": "not_running"
          },
          "max_operations_ttl": 2,
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
          "baker": "tz1YCABRTa6H8PLKx2EtDWeCGPaKxUhNgv47",
          "level": {
              "level": 2,
              "level_position": 1,
              "cycle": 0,
              "cycle_position": 1,
              "voting_period": 0,
              "voting_period_position": 1,
              "expected_commitment": false
          },
          "voting_period_kind": "proposal",
          "nonce_hash": null,
          "consumed_gas": "0",
          "deactivated": [],
          "balance_updates": []
      },
      "operations": [
          [],
          [],
          [],
          []
      ]
    }""",
    """{
      "protocol": "Ps6mwMrF2ER2s51cp9yYpjDcuzQjsc2yAz8bQsRgdaRxw4Fk95H",
      "chain_id": "NetXgtSLGNJvNye",
      "hash": "BMPtRJqFGQJRTfn8bXQR2grLE1M97XnUmG5vgjHMW7St1Wub7Cd",
      "header": {
        "level": 1,
        "proto": 1,
        "predecessor": "BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe",
        "timestamp": "2018-11-30T18:05:38Z",
        "validation_pass": 0,
        "operations_hash": "LLoZS2LW3rEi7KYU4ouBQtorua37aWWCtpDmv1n2x3xoKi6sVXLWp",
        "fitness": [
          "00",
          "0000000000000001"
        ],
        "context": "CoV16kW8WgL51SpcftQKdeqc94D6ekghMgPMmEn7TSZzFA697PeE",
        "content": {
          "command": "activate",
          "hash": "PsddFKi32cMJ2qPjf43Qv5GDWLDPZb3T3bF6fLKiF5HtvHNU7aP",
          "fitness": [
            "00",
            "0000000000000001"
          ],
          "protocol_parameters": ""
        },
        "signature": "sigsHvZaQcoVcXZ17UszTuaGRQuc8TYu3uczhhfNKeGtEZ7wgTq4mLmbiAUSyvGKM49tsBoemkqNJbUEELCe31ppNSKbkvck"
      },
      "metadata": {
        "max_block_header_length": 238,
        "max_operation_data_length": 16384,
        "max_operation_list_length": [
          {
            "max_op": 32,
            "max_size": 32768
          },
          {
            "max_size": 32768
          },
          {
            "max_op": 132,
            "max_size": 135168
          },
          {
            "max_size": 524288
          }
        ],
        "max_operations_ttl": 1,
        "next_protocol": "PsddFKi32cMJ2qPjf43Qv5GDWLDPZb3T3bF6fLKiF5HtvHNU7aP",
        "protocol": "Ps6mwMrF2ER2s51cp9yYpjDcuzQjsc2yAz8bQsRgdaRxw4Fk95H",
        "test_chain_status": {
          "status": "not_running"
        }
      },
      "operations": []
    }""",
    genesisBlockResponse
  )

}
