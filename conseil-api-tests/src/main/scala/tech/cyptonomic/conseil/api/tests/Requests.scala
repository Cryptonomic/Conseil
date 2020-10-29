package tech.cyptonomic.conseil.api.tests

object Requests {

  val CONSEIL_NETWORK: String = "mainnet"

  object ConseilRequests {

    final val CONSEIL_BUILD_INFO: String = "/info"

    final val CONSEIL_PLATFORMS: String = "/v2/metadata/platforms"

  }

  object TezosConfig {

    final val TEZOS_NETWORKS: String = "/v2/metadata/tezos/networks"

    final val TEZOS_ENTITIES: String = "/v2/metadata/tezos/" + CONSEIL_NETWORK + "/entities"

  }

  final val TEZOS_ENTITIES: Array[String] = Array(
    "accounts",
    "accounts_history",
    "baker_registry",
    "bakers",
    "baking_rights",
    "balance_updates",
    "big_map_contents",
    "big_maps",
    "blocks",
    "fees",
    "governance",
    "known_addresses",
    "operation_groups",
    "operations",
    "originated_account_maps",
    "registered_tokens"
  )

  //    Stores the known attributes of each entity to call
  final val TEZOS_ENTITY_ATTRIBUTES: Map[String, Array[String]] = Map(
    ("accounts" -> Array(
      "account_id",
      "block_id",
      "counter",
      "script",
      "storage",
      "balance",
      "block_level",
      "manager",
      "spendable",
      "delegate_setable",
      "delegate_value",
      "is_baker",
      "is_activated"
    )),
    ("accounts_history" -> Array(
      "account_id",
      "block_id",
      "counter",
      "storage",
      "balance",
      "block_level",
      "delegate_value",
      "asof",
      "is_baker",
      "cycle",
      "is_activated",
      "is_active_baker"
    )),
    ("baker_registry" -> Array(
      "name",
      "is_accepting_delegation",
      "external_data_url",
      "split",
      "payment_accounts",
      "minimum_delegation",
      "payout_delay",
      "payout_frequency",
      "minimum_payout",
      "is_cheap",
      "pay_for_own_blocks",
      "pay_for_endorsements",
      "pay_gained_fees",
      "pay_for_accusation_gains",
      "subtract_lost_deposits_when_accused",
      "subtract_lost_fees_when_accused",
      "pay_for_revelation",
      "subtract_lost_rewards_when_miss_revelation",
      "subtract_lost_fees_when_miss_revelation",
      "compensate_missed_blocks",
      "pay_for_stolen_blocks",
      "compensate_missed_endorsements",
      "compensate_low_priority_endorsement_loss",
      "overdelegation_threshold",
      "subtract_rewards_from_uninvited_delegation",
      "record_manager",
      "timestamp"
    )),
    ("bakers" -> Array(
      "pkh",
      "block_id",
      "balance",
      "frozen_balance",
      "staking_balance",
      "delegated_balance",
      "rolls",
      "deactivated",
      "grace_period",
      "block_level",
      "cycle",
      "period"
    )),
    ("baking_rights" -> Array(
      "block_hash",
      "block_level",
      "delegate",
      "priority",
      "estimated_time",
      "cycle",
      "governance_period"
    )),
    ("balance_updates" -> Array(
      "source",
      "source_id",
      "source_hash",
      "kind",
      "account_id",
      "change",
      "level",
      "category",
      "operation_group_hash",
      "block_id",
      "block_level",
      "cycle",
      "period"
    )),
    ("big_map_contents" -> Array(
      "big_map_id",
      "key",
      "key_hash",
      "operation_group_id",
      "value"
    )),
    ("big_maps" -> Array(
      "big_map_id",
      "key_type",
      "value_type",
    )),
    ("blocks" -> Array(
      "level",
      "proto",
      "predecessor",
      "timestamp",
      "fitness",
      "context",
      "signature",
      "protocol",
      "chain_id",
      "hash",
      "operations_hash",
      "period_kind",
      "current_expected_quorum",
      "active_proposal",
      "baker",
      "consumed_gas",
      "meta_cycle",
      "meta_cycle_position",
      "meta_voting_period",
      "meta_voting_period_position",
      "priority",
      "utc_year",
      "utc_month",
      "utc_day",
      "utc_time"
    )),
    ("fees" -> Array(
      "low",
      "medium",
      "high",
      "timestamp",
      "kind",
      "cycle",
      "level"
    )),
    ("governance" -> Array(
      "voting_period",
      "voting_period_kind",
      "cycle",
      "level",
      "block_hash",
      "proposal_hash",
      "yay_count",
      "nay_count",
      "pass_count",
      "yay_rolls",
      "nay_rolls",
      "pass_rolls",
      "total_rolls",
      "block_yay_count",
      "block_nay_count",
      "block_pass_count",
      "block_yay_rolls",
      "block_nay_rolls",
      "block_pass_rolls"
    )),
    ("operation_groups" -> Array(
      "protocol",
      "chain_id",
      "hash",
      "branch",
      "signature",
      "block_id",
      "block_level"
    )),
    ("operations" -> Array(
      "timestamp",
      "block_level",
      "source",
      "destination",
      "amount",
      "kind",
      "fee",
      "status",
      "operation_group_hash",
      "balance",
      "ballot",
      "utc_year",
      "utc_time",
      "utc_month",
      "utc_day",
      "storage_size",
      "storage_limit",
      "storage",
      "spendable",
      "number_of_slots",
      "slots",
      "secret",
      "script",
      "public_key",
      "proposal",
      "pkh",
      "period",
      "parameters_micheline",
      "parameters_entrypoints",
      "parameters",
      "paid_storage_size_diff",
      "originated_contracts",
      "nonce",
      "manager_pubkey",
      "level",
      "internal",
      "gas_limit",
      "errors",
      "delegate",
      "delegatable",
      "cycle",
      "counter",
      "consumed_gas",
      "branch",
      "block_hash",
      "ballot_period"
    )),
    ("originated_account_maps" -> Array(
      "big_map_id",
      "account_id"
    )),
    ("registered_tokens" -> Array(
      "id",
      "name",
      "contract_type",
      "account_id",
      "scale"
    ))
  )


  /**
   * Generate a path to get attributes for a given entity
   *
   * @param entity the entity to get attribtues for
   * @return the conseil api endpoint for that specific entity's attributes
   */
  def getTezosAttributePath(entity: String): String = "/v2/metadata/tezos/" + CONSEIL_NETWORK + "/" + entity + "/attributes"

  def getTezosQueryPath(entity: String): String = "/v2/data/tezos/" + CONSEIL_NETWORK + "/" + entity

  object TezosChainRequests {

    final val TEZOS_OPERATION_KINDS: String = "/v2/metadata/tezos/" + CONSEIL_NETWORK + "/operations/kind"

    final val TEZOS_BLOCK_HEAD: String = "/v2/data/tezos/" + CONSEIL_NETWORK + "/blocks/head"

    final val TEZOS_GET_OPERATION_GROUP: String = "/v2/data/tezos/" + CONSEIL_NETWORK + "/operation_groups/ooJQqGxrsdxi9GNinZjivnx8y7BtMc372G8gB7GmCKSQRnNkEtK"

    final val TEZOS_GET_ACCOUNT: String = "/v2/data/tezos/" + CONSEIL_NETWORK + "/accounts/KT1V7VoyjbvqSmnRtv9pHkRuBCPT7UubCrCX"


  }

}
