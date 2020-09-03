package tech.cryptonomic.conseil.smoke.tests

trait RegressionFixtures {

  object GroupingPredicatesQuery {
    lazy val requestJsonPayload =
      """
      {
        "predicates": [
          {
            "field": "source",
            "set": [
              "tz1VmUWL8DxseZnPTdhHQkkuk6nK55NVdKCG"
            ],
            "operation": "eq",
            "group": "A0"
          },
          {
            "field": "kind",
            "set": [
              "transaction"
            ],
            "operation": "eq",
            "group": "A0"
          },
          {
            "field": "operation_group_hash",
            "operation": "isnull",
            "inverse": true,
            "group": "A0"
          },
          {
            "field": "destination",
            "set": [
              "tz1VmUWL8DxseZnPTdhHQkkuk6nK55NVdKCG"
            ],
            "operation": "eq",
            "group": "A1"
          },
          {
            "field": "kind",
            "set": [
              "transaction"
            ],
            "operation": "eq",
            "group": "A1"
          },
          {
            "field": "operation_group_hash",
            "operation": "isnull",
            "inverse": true,
            "group": "A1"
          }
        ],
        "orderBy": [
          {
            "field": "block_level",
            "direction": "desc"
          }
        ],
        "limit": 10
      }
      """

    lazy val responseJsonContent =
      """
        [
          {
            "secret" : null,
            "storage_size" : 579,
            "number_of_slots" : null,
            "utc_year" : 2019,
            "internal" : false,
            "delegatable" : null,
            "source" : "tz1VmUWL8DxseZnPTdhHQkkuk6nK55NVdKCG",
            "consumed_gas" : 21704,
            "timestamp" : 1575046195000,
            "pkh" : null,
            "nonce" : null,
            "parameters_micheline" : "{\"prim\":\"Pair\",\"args\":[{\"prim\":\"None\"},{\"prim\":\"Pair\",\"args\":[{\"string\":\"FIRST COMMIT HASH\"},{\"string\":\"dingdingding\"}]}]}",
            "utc_day" : 29,
            "block_level" : 806,
            "branch" : null,
            "utc_month" : 11,
            "errors" : null,
            "balance" : null,
            "operation_group_hash" : "onfzRcvTz5wjz5Gx3BGtyAQr1DqykYqbrWoFjLP3CyB7gAa4cR2",
            "public_key" : null,
            "paid_storage_size_diff" : null,
            "amount" : 0,
            "delegate" : null,
            "parameters_entrypoints" : "default",
            "utc_time" : "17:49:55",
            "proposal" : null,
            "block_hash" : "BL1ehjm3hRT85WF4VbCRaU6U6TNQmGkAQVKXJqeep1APmiPqRcv",
            "spendable" : null,
            "cycle" : 0,
            "status" : "applied",
            "manager_pubkey" : null,
            "slots" : null,
            "storage_limit" : 0,
            "ballot_period" : null,
            "storage" : null,
            "counter" : 213,
            "script" : null,
            "kind" : "transaction",
            "originated_contracts" : null,
            "gas_limit" : 21804,
            "parameters" : "Pair None (Pair \"FIRST COMMIT HASH\" \"dingdingding\")",
            "destination" : "KT1T2V8prXxe2uwanMim7TYHsXMmsrygGbxG",
            "ballot" : null,
            "period" : 0,
            "fee" : 2481,
            "level" : null
          },
          {
            "secret" : null,
            "storage_size" : 584,
            "number_of_slots" : null,
            "utc_year" : 2019,
            "internal" : false,
            "delegatable" : null,
            "source" : "tz1VmUWL8DxseZnPTdhHQkkuk6nK55NVdKCG",
            "consumed_gas" : 21727,
            "timestamp" : 1575046075000,
            "pkh" : null,
            "nonce" : null,
            "parameters_micheline" : "{\"prim\":\"Pair\",\"args\":[{\"prim\":\"None\"},{\"prim\":\"Pair\",\"args\":[{\"string\":\"Wulalalalala\"},{\"string\":\"FIRST COMMIT HASH\"}]}]}",
            "utc_day" : 29,
            "block_level" : 802,
            "branch" : null,
            "utc_month" : 11,
            "errors" : null,
            "balance" : null,
            "operation_group_hash" : "opZA3n62cz57KkQw1C7Uu3Ap5frUmzCf8hhzSGGtda1hVU5nEED",
            "public_key" : null,
            "paid_storage_size_diff" : 5,
            "amount" : 0,
            "delegate" : null,
            "parameters_entrypoints" : "default",
            "utc_time" : "17:47:55",
            "proposal" : null,
            "block_hash" : "BL22FnAq7PuTEj42hZEvDmPU3E4qvdEayPkzCZJs3XVJGiED7x5",
            "spendable" : null,
            "cycle" : 0,
            "status" : "applied",
            "manager_pubkey" : null,
            "slots" : null,
            "storage_limit" : 25,
            "ballot_period" : null,
            "storage" : null,
            "counter" : 212,
            "script" : null,
            "kind" : "transaction",
            "originated_contracts" : null,
            "gas_limit" : 21827,
            "parameters" : "Pair None (Pair \"Wulalalalala\" \"FIRST COMMIT HASH\")",
            "destination" : "KT1T2V8prXxe2uwanMim7TYHsXMmsrygGbxG",
            "ballot" : null,
            "period" : 0,
            "fee" : 2483,
            "level" : null
          },
          {
            "secret" : null,
            "storage_size" : null,
            "number_of_slots" : null,
            "utc_year" : 2019,
            "internal" : false,
            "delegatable" : null,
            "source" : "tz1g4Kw2qhYELxeeHc2yiuLtPdovVckYNc6G",
            "consumed_gas" : 10207,
            "timestamp" : 1575043055000,
            "pkh" : null,
            "nonce" : null,
            "parameters_micheline" : null,
            "utc_day" : 29,
            "block_level" : 708,
            "branch" : null,
            "utc_month" : 11,
            "errors" : null,
            "balance" : null,
            "operation_group_hash" : "ooqYKvJXTjwN8ongLNaf9A3k2sENGeyycZbMRwHTABPmXEtoEWJ",
            "public_key" : null,
            "paid_storage_size_diff" : null,
            "amount" : 100000000,
            "delegate" : null,
            "parameters_entrypoints" : null,
            "utc_time" : "16:57:35",
            "proposal" : null,
            "block_hash" : "BLhJxiRHN3xN2RNk8VCvLK9ssdoiV1g5JPP7nJS4z9if5YxQvva",
            "spendable" : null,
            "cycle" : 0,
            "status" : "applied",
            "manager_pubkey" : null,
            "slots" : null,
            "storage_limit" : 277,
            "ballot_period" : null,
            "storage" : null,
            "counter" : 180,
            "script" : null,
            "kind" : "transaction",
            "originated_contracts" : null,
            "gas_limit" : 10307,
            "parameters" : null,
            "destination" : "tz1VmUWL8DxseZnPTdhHQkkuk6nK55NVdKCG",
            "ballot" : null,
            "period" : 0,
            "fee" : 1188,
            "level" : null
          }
        ]
      """
  }

}
