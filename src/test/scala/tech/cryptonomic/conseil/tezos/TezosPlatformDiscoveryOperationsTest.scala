package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp
import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, DataType, KeyType, Network}
import tech.cryptonomic.conseil.config.Newest
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.util.ConfigUtil

import scala.concurrent.duration._


class TezosPlatformDiscoveryOperationsTest
  extends WordSpec
    with InMemoryDatabase
    with MockFactory
    with Matchers
    with ScalaFutures
    with OptionValues
    with IntegrationPatience
    with LazyLogging {

  import slick.jdbc.PostgresProfile.api._
  import scala.concurrent.ExecutionContext.Implicits.global
  import tech.cryptonomic.conseil.config.Platforms._

  import scala.concurrent.ExecutionContext.Implicits.global

  "getNetworks" should {
    "return list with one element" in {
      val config = PlatformsConfiguration(
        platforms = Map(
          Tezos -> List(TezosConfiguration("alphanet", TezosNodeConfiguration(protocol = "http", hostname = "localhost", port = 8732)))
        )
      )

      ConfigUtil.getNetworks(config, "tezos") shouldBe List(Network("alphanet", "Alphanet", "tezos", "alphanet"))
    }

    "return two networks" in {
      val config = PlatformsConfiguration(
        platforms = Map(
          Tezos -> List(
            TezosConfiguration(
              "alphanet",
              TezosNodeConfiguration(protocol = "http", hostname = "localhost", port = 8732)
            ),
            TezosConfiguration(
              "alphanet-staging",
              TezosNodeConfiguration(protocol = "https", hostname = "nautilus.cryptonomic.tech", port = 8732, pathPrefix = "tezos/alphanet/")
            )
          )
        )
      )
      ConfigUtil.getNetworks(config, "tezos") should have size 2
    }
  }

  "getEntities" should {

    "return list of attributes of Fees" in {

      dbHandler.run {
        TezosPlatformDiscoveryOperations.makeAttributesList("fees")
      }.futureValue shouldBe
        List(
          Attribute("low", "Low", DataType.Int, None, KeyType.UniqueKey, "fees"),
          Attribute("medium", "Medium", DataType.Int, None, KeyType.UniqueKey, "fees"),
          Attribute("high", "High", DataType.Int, None, KeyType.UniqueKey, "fees"),
          Attribute("timestamp", "Timestamp", DataType.DateTime, None, KeyType.UniqueKey, "fees"),
          Attribute("kind", "Kind", DataType.String, Some(0), KeyType.UniqueKey, "fees")
        )
    }

    "return list of attributes of accounts" in {
      dbHandler.run {
        TezosPlatformDiscoveryOperations.makeAttributesList("accounts")
      }.futureValue shouldBe
        List(
          Attribute("account_id", "Account id", DataType.String, Some(0), KeyType.UniqueKey, "accounts"),
          Attribute("block_id", "Block id", DataType.String, Some(0), KeyType.UniqueKey, "accounts"),
          Attribute("manager", "Manager", DataType.String, Some(0), KeyType.UniqueKey, "accounts"),
          Attribute("spendable", "Spendable", DataType.Boolean, Some(0), KeyType.UniqueKey, "accounts"),
          Attribute("delegate_setable", "Delegate setable", DataType.Boolean, Some(0), KeyType.UniqueKey, "accounts"),
          Attribute("delegate_value", "Delegate value", DataType.String, Some(0), KeyType.UniqueKey, "accounts"),
          Attribute("counter", "Counter", DataType.Int, None, KeyType.UniqueKey, "accounts"),
          Attribute("script", "Script", DataType.String, Some(0), KeyType.UniqueKey, "accounts"),
          Attribute("balance", "Balance", DataType.Decimal, None, KeyType.UniqueKey, "accounts"),
          Attribute("block_level", "Block level", DataType.Decimal, None, KeyType.UniqueKey, "accounts")
        )
    }

    "return list of attributes of blocks" in {
      dbHandler.run {
        TezosPlatformDiscoveryOperations.makeAttributesList("blocks")
      }.futureValue shouldBe
        List(
          Attribute("level", "Level", DataType.Int, None, KeyType.UniqueKey, "blocks"),
          Attribute("proto", "Proto", DataType.Int, None, KeyType.UniqueKey, "blocks"),
          Attribute("predecessor", "Predecessor", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("timestamp", "Timestamp", DataType.DateTime, None, KeyType.UniqueKey, "blocks"),
          Attribute("validation_pass", "Validation pass", DataType.Int, None, KeyType.UniqueKey, "blocks"),
          Attribute("fitness", "Fitness", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("context", "Context", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("signature", "Signature", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("protocol", "Protocol", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("chain_id", "Chain id", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("hash", "Hash", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("operations_hash", "Operations hash", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("period_kind", "Period kind", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("current_expected_quorum", "Current expected quorum", DataType.Int, None, KeyType.UniqueKey, "blocks"),
          Attribute("active_proposal", "Active proposal", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("baker", "Baker", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("nonce_hash", "Nonce hash", DataType.String, Some(0), KeyType.UniqueKey, "blocks"),
          Attribute("consumed_gas", "Consumed gas", DataType.Decimal, None, KeyType.UniqueKey, "blocks"),
          Attribute("meta_level", "Meta level", DataType.Int, None, KeyType.UniqueKey, "blocks"),
          Attribute("meta_level_position", "Meta level position", DataType.Int, None, KeyType.UniqueKey, "blocks"),
          Attribute("meta_cycle", "Meta cycle", DataType.Int, None, KeyType.UniqueKey, "blocks"),
          Attribute("meta_cycle_position", "Meta cycle position", DataType.Int, None, KeyType.UniqueKey, "blocks"),
          Attribute("meta_voting_period", "Meta voting period", DataType.Int, None, KeyType.UniqueKey, "blocks"),
          Attribute("meta_voting_period_position", "Meta voting period position", DataType.Int, None, KeyType.UniqueKey, "blocks"),
          Attribute("expected_commitment", "Expected commitment", DataType.Boolean, Some(0), KeyType.UniqueKey, "blocks")
        )
    }

    "return list of attributes of operations" in {
      dbHandler.run {
        TezosPlatformDiscoveryOperations.makeAttributesList("operations")
      }.futureValue shouldBe
        List(
          Attribute("operation_id", "Operation id", DataType.Int, None, KeyType.UniqueKey, "operations"),
          Attribute("operation_group_hash", "Operation group hash", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("kind", "Kind", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("level", "Level", DataType.Int, None, KeyType.UniqueKey, "operations"),
          Attribute("delegate", "Delegate", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("slots", "Slots", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("nonce", "Nonce", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("pkh", "Pkh", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("secret", "Secret", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("source", "Source", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("fee", "Fee", DataType.Decimal, None, KeyType.UniqueKey, "operations"),
          Attribute("counter", "Counter", DataType.Decimal, None, KeyType.UniqueKey, "operations"),
          Attribute("gas_limit", "Gas limit", DataType.Decimal, None, KeyType.UniqueKey, "operations"),
          Attribute("storage_limit", "Storage limit", DataType.Decimal, None, KeyType.UniqueKey, "operations"),
          Attribute("public_key", "Public key", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("amount", "Amount", DataType.Decimal, None, KeyType.UniqueKey, "operations"),
          Attribute("destination", "Destination", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("parameters", "Parameters", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("manager_pubkey", "Manager pubkey", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("balance", "Balance", DataType.Decimal, None, KeyType.UniqueKey, "operations"),
          Attribute("spendable", "Spendable", DataType.Boolean, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("delegatable", "Delegatable", DataType.Boolean, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("script", "Script", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("status", "Status", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("consumed_gas", "Consumed gas", DataType.Decimal, None, KeyType.UniqueKey, "operations"),
          Attribute("block_hash", "Block hash", DataType.String, Some(0), KeyType.UniqueKey, "operations"),
          Attribute("block_level", "Block level", DataType.Int, None, KeyType.UniqueKey, "operations"),
          Attribute("timestamp", "Timestamp", DataType.DateTime, None, KeyType.UniqueKey, "operations")
        )
    }

    "return list of attributes of operation groups" in {
      dbHandler.run {
        TezosPlatformDiscoveryOperations.makeAttributesList("operation_groups")
      }.futureValue shouldBe
        List(
          Attribute("protocol", "Protocol", DataType.String, Some(0), KeyType.UniqueKey, "operation_groups"),
          Attribute("chain_id", "Chain id", DataType.String, Some(0), KeyType.UniqueKey, "operation_groups"),
          Attribute("hash", "Hash", DataType.String, Some(0), KeyType.UniqueKey, "operation_groups"),
          Attribute("branch", "Branch", DataType.String, Some(0), KeyType.UniqueKey, "operation_groups"),
          Attribute("signature", "Signature", DataType.String, Some(0), KeyType.UniqueKey, "operation_groups"),
          Attribute("block_id", "Block id", DataType.String, Some(0), KeyType.UniqueKey, "operation_groups")
        )
    }

    "return empty list for non existing table" in {
      dbHandler.run {
        TezosPlatformDiscoveryOperations.makeAttributesList("nonExisting")
      }.futureValue shouldBe List.empty
    }
  }

  "listAttributeValues" should {

    "return list of values of kind attribute of Fees without filter" in {
      val avgFee = AverageFees(1, 3, 5, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)), "example1")
      dbHandler.run(TezosDatabaseOperations.writeFees(List(avgFee))).isReadyWithin(5.seconds)

      dbHandler.run(
        TezosPlatformDiscoveryOperations.verifyAttributesAndGetQueries("fees", "kind", None)
      ).futureValue shouldBe List("example1")
    }

    "returns a failed future when asked for medium attribute of Fees without filter - numeric attributes should not be displayed" in {
      val avgFee = AverageFees(1, 3, 5, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)), "example1")

      dbHandler.run(TezosDatabaseOperations.writeFees(List(avgFee))).isReadyWithin(5.seconds)

      intercept[NoSuchElementException] {
        throw dbHandler.run(
          TezosPlatformDiscoveryOperations.verifyAttributesAndGetQueries("fees", "medium", None)
        ).failed.futureValue
      }
    }

    "return empty list when trying to sql inject" in {
      val avgFee = AverageFees(1, 3, 5, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)), "example1")

      dbHandler.run(TezosDatabaseOperations.writeFees(List(avgFee))).isReadyWithin(5.seconds)
      // That's how the SLQ-injected string will look like:
      // SELECT DISTINCT kind FROM fees WHERE kind LIKE '%'; DELETE FROM fees WHERE kind LIKE '%'
      val maliciousFilter = Some("'; DELETE FROM fees WHERE kind LIKE '")

      dbHandler.run(
        TezosPlatformDiscoveryOperations.verifyAttributesAndGetQueries("fees", "kind", maliciousFilter)
      ).futureValue shouldBe List.empty

      dbHandler.run(Tables.Fees.length.result).futureValue shouldBe 1

    }
    "correctly apply the filter" in {
      val avgFees = List(
        AverageFees(1, 3, 5, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)), "example1"),
        AverageFees(2, 4, 6, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 31)), "example2")
      )

      dbHandler.run(
        TezosPlatformDiscoveryOperations.verifyAttributesAndGetQueries("fees", "kind", Some("1"))
      ).futureValue shouldBe List.empty
      dbHandler.run(TezosDatabaseOperations.writeFees(avgFees)).isReadyWithin(5.seconds)
      dbHandler.run(
        TezosPlatformDiscoveryOperations.verifyAttributesAndGetQueries("fees", "kind", None)
      ).futureValue should contain theSameElementsAs List("example1", "example2")
      dbHandler.run(
        TezosPlatformDiscoveryOperations.verifyAttributesAndGetQueries("fees", "kind", Some("ex"))
      ).futureValue should contain theSameElementsAs List("example1", "example2")
      dbHandler.run(
        TezosPlatformDiscoveryOperations.verifyAttributesAndGetQueries("fees", "kind", Some("ample"))
      ).futureValue should contain theSameElementsAs List("example1", "example2")
      dbHandler.run(
        TezosPlatformDiscoveryOperations.verifyAttributesAndGetQueries("fees", "kind", Some("1"))
      ).futureValue shouldBe List("example1")

    }

    "should validate correctly fields" in {
      TezosPlatformDiscoveryOperations.areFieldsValid("fees", Set("low", "medium", "high", "timestamp", "kind")) shouldBe true
    }
    "should validate correctly fields when only some of them are selected" in {
      TezosPlatformDiscoveryOperations.areFieldsValid("fees", Set("low", "medium", "kind")) shouldBe true
    }
    "should return false when there will be field not existing in the DB" in {
      TezosPlatformDiscoveryOperations.areFieldsValid("fees", Set("low", "medium", "kind", "WRONG")) shouldBe false
    }

  }

}
