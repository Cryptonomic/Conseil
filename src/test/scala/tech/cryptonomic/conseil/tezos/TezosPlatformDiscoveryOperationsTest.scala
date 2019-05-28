package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp
import java.time.LocalDateTime

import cats.effect.concurrent.MVar
import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import slick.dbio
import tech.cryptonomic.conseil.config.MetadataConfiguration
import tech.cryptonomic.conseil.generic.chain.DataTypes.{HighCardinalityAttribute, InvalidAttributeDataType, InvalidAttributeFilterLength}
import tech.cryptonomic.conseil.generic.chain.MetadataOperations
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.metadata.AttributeValuesCacheConfiguration
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.tezos.MetadataCaching._
import tech.cryptonomic.conseil.util.ConfigUtil

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

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
  import tech.cryptonomic.conseil.config.Platforms._

  import scala.concurrent.ExecutionContext.Implicits.global

  val metadataOperations: MetadataOperations = new MetadataOperations {
    override def runQuery[A](action: dbio.DBIO[A]) = dbHandler.run(action)
  }
  implicit val contextShift: ContextShift[IO] = IO.contextShift(implicitly[ExecutionContext])

  val cachingStatus = MVar[IO].of[CachingStatus](NotStarted).unsafeRunSync()
  val attributesCache = MVar[IO].empty[AttributesCache].unsafeRunSync()
  val entitiesCache = MVar[IO].empty[EntitiesCache].unsafeRunSync()
  val attributeValuesCache = MVar[IO].empty[AttributeValuesCache].unsafeRunSync()
  val metadataCaching = new MetadataCaching[IO](cachingStatus, attributesCache, entitiesCache, attributeValuesCache)
  val metadadataConfiguration = new MetadataConfiguration(Map.empty)
  val cacheConfiguration = new AttributeValuesCacheConfiguration(metadadataConfiguration)
  val sut = TezosPlatformDiscoveryOperations(metadataOperations, metadataCaching, cacheConfiguration, 10 seconds)

  override def beforeAll(): Unit = {
    super.beforeAll()
    sut.init()
    ()
  }

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

      sut.getTableAttributes("fees").futureValue shouldBe
        Some(
          List(
            Attribute("low", "Low", DataType.Int, None, KeyType.NonKey, "fees"),
            Attribute("medium", "Medium", DataType.Int, None, KeyType.NonKey, "fees"),
            Attribute("high", "High", DataType.Int, None, KeyType.NonKey, "fees"),
            Attribute("timestamp", "Timestamp", DataType.DateTime, None, KeyType.NonKey, "fees"),
            Attribute("kind", "Kind", DataType.String, None, KeyType.NonKey, "fees")
          )
        )
    }

    "return list of attributes of accounts" in {
      sut.getTableAttributes("accounts").futureValue shouldBe
        Some(
          List(
            Attribute("account_id", "Account id", DataType.String, None, KeyType.UniqueKey, "accounts"),
            Attribute("block_id", "Block id", DataType.String, None, KeyType.NonKey, "accounts"),
            Attribute("manager", "Manager", DataType.String, None, KeyType.UniqueKey, "accounts"),
            Attribute("spendable", "Spendable", DataType.Boolean, None, KeyType.NonKey, "accounts"),
            Attribute("delegate_setable", "Delegate setable", DataType.Boolean, None, KeyType.NonKey, "accounts"),
            Attribute("delegate_value", "Delegate value", DataType.String, None, KeyType.NonKey, "accounts"),
            Attribute("counter", "Counter", DataType.Int, None, KeyType.NonKey, "accounts"),
            Attribute("script", "Script", DataType.String, None, KeyType.NonKey, "accounts"),
            Attribute("storage", "Storage", DataType.String, None, KeyType.NonKey, "accounts"),
            Attribute("balance", "Balance", DataType.Decimal, None, KeyType.NonKey, "accounts"),
            Attribute("block_level", "Block level", DataType.Decimal, None, KeyType.UniqueKey, "accounts")
          )
        )
    }

    "return list of attributes of blocks" in {
      sut.getTableAttributes("blocks").futureValue shouldBe
        Some(
          List(
            Attribute("level", "Level", DataType.Int, None, KeyType.UniqueKey, "blocks"),
            Attribute("proto", "Proto", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("predecessor", "Predecessor", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("timestamp", "Timestamp", DataType.DateTime, None, KeyType.NonKey, "blocks"),
            Attribute("validation_pass", "Validation pass", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("fitness", "Fitness", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("context", "Context", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("signature", "Signature", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("protocol", "Protocol", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("chain_id", "Chain id", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("hash", "Hash", DataType.String, None, KeyType.UniqueKey, "blocks"),
            Attribute("operations_hash", "Operations hash", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("period_kind", "Period kind", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("current_expected_quorum", "Current expected quorum", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("active_proposal", "Active proposal", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("baker", "Baker", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("nonce_hash", "Nonce hash", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("consumed_gas", "Consumed gas", DataType.Decimal, None, KeyType.NonKey, "blocks"),
            Attribute("meta_level", "Meta level", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("meta_level_position", "Meta level position", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("meta_cycle", "Meta cycle", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("meta_cycle_position", "Meta cycle position", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("meta_voting_period", "Meta voting period", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("meta_voting_period_position", "Meta voting period position", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("expected_commitment", "Expected commitment", DataType.Boolean, None, KeyType.NonKey, "blocks")
          )
        )
    }

    "return list of attributes of operations" in {
      sut.getTableAttributes("operations").futureValue shouldBe
        Some(
          List(
            Attribute("operation_id", "Operation id", DataType.Int, None, KeyType.UniqueKey, "operations"),
            Attribute("operation_group_hash", "Operation group hash", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("kind", "Kind", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("level", "Level", DataType.Int, None, KeyType.NonKey, "operations"),
            Attribute("delegate", "Delegate", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("slots", "Slots", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("nonce", "Nonce", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("pkh", "Pkh", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("secret", "Secret", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("source", "Source", DataType.String, None, KeyType.UniqueKey, "operations"),
            Attribute("fee", "Fee", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("counter", "Counter", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("gas_limit", "Gas limit", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("storage_limit", "Storage limit", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("public_key", "Public key", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("amount", "Amount", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("destination", "Destination", DataType.String, None, KeyType.UniqueKey, "operations"),
            Attribute("parameters", "Parameters", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("manager_pubkey", "Manager pubkey", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("balance", "Balance", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("spendable", "Spendable", DataType.Boolean, None, KeyType.NonKey, "operations"),
            Attribute("delegatable", "Delegatable", DataType.Boolean, None, KeyType.NonKey, "operations"),
            Attribute("script", "Script", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("storage", "Storage", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("status", "Status", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("consumed_gas", "Consumed gas", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("storage_size", "Storage size", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("paid_storage_size_diff", "Paid storage size diff", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("block_hash", "Block hash", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("block_level", "Block level", DataType.Int, None, KeyType.NonKey, "operations"),
            Attribute("timestamp", "Timestamp", DataType.DateTime, None, KeyType.NonKey, "operations")
          )
        )
    }

    "return list of attributes of operation groups" in {

      sut.getTableAttributes("operation_groups").futureValue shouldBe
        Some(
          List(
            Attribute("protocol", "Protocol", DataType.String, None, KeyType.NonKey, "operation_groups"),
            Attribute("chain_id", "Chain id", DataType.String, None, KeyType.NonKey, "operation_groups"),
            Attribute("hash", "Hash", DataType.String, None, KeyType.UniqueKey, "operation_groups"),
            Attribute("branch", "Branch", DataType.String, None, KeyType.NonKey, "operation_groups"),
            Attribute("signature", "Signature", DataType.String, None, KeyType.NonKey, "operation_groups"),
            Attribute("block_id", "Block id", DataType.String, None, KeyType.NonKey, "operation_groups")
          )
        )
    }

    "return list of attributes of delegates" in {

      sut.getTableAttributes("delegates").futureValue shouldBe
        Some(
          List(
            Attribute("pkh", "Pkh", DataType.String, None, KeyType.UniqueKey, "delegates"),
            Attribute("block_id", "Block id", DataType.String, None, KeyType.NonKey, "delegates"),
            Attribute("balance", "Balance", DataType.Decimal, None, KeyType.NonKey, "delegates"),
            Attribute("frozen_balance", "Frozen balance", DataType.Decimal, None, KeyType.NonKey, "delegates"),
            Attribute("staking_balance", "Staking balance", DataType.Decimal, None, KeyType.NonKey, "delegates"),
            Attribute("delegated_balance", "Delegated balance", DataType.Decimal, None, KeyType.NonKey, "delegates"),
            Attribute("deactivated", "Deactivated", DataType.Boolean, None, KeyType.NonKey, "delegates"),
            Attribute("grace_period", "Grace period", DataType.Int, None, KeyType.NonKey, "delegates"),
            Attribute("block_level", "Block level", DataType.Int, None, KeyType.NonKey, "delegates")
          )
        )
    }

    "return list of attributes of proposals" in {

      sut.getTableAttributes("proposals").futureValue shouldBe
        Some(
          List(
            Attribute("protocol_hash", "Protocol hash", DataType.String, None, KeyType.UniqueKey, "proposals"),
            Attribute("block_id", "Block id", DataType.String, None, KeyType.NonKey, "proposals"),
            Attribute("block_level", "Block level", DataType.Int, None, KeyType.NonKey, "proposals")
          )
        )
    }

    "return list of attributes of rolls" in {

      sut.getTableAttributes("rolls").futureValue shouldBe
        Some(
          List(
            Attribute("pkh", "Pkh", DataType.String, None, KeyType.NonKey, "rolls"),
            Attribute("rolls", "Rolls", DataType.Int, None, KeyType.NonKey, "rolls"),
            Attribute("block_id", "Block id", DataType.String, None, KeyType.NonKey, "rolls"),
            Attribute("block_level", "Block level", DataType.Int, None, KeyType.NonKey, "rolls")
          )
        )
    }

    "return list of attributes of ballots" in {

      sut.getTableAttributes("ballots").futureValue shouldBe
        Some(
          List(
            Attribute("pkh", "Pkh", DataType.String, None, KeyType.NonKey, "ballots"),
            Attribute("ballot", "Ballot", DataType.String, None, KeyType.NonKey, "ballots"),
            Attribute("block_id", "Block id", DataType.String, None, KeyType.NonKey, "ballots"),
            Attribute("block_level", "Block level", DataType.Int, None, KeyType.NonKey, "ballots")
          )
        )
    }

    "return empty list for non existing table" in {
      sut.getTableAttributes("nonExisting").futureValue shouldBe None
    }
  }

  "listAttributeValues" should {

    "return list of values of kind attribute of Fees without filter" in {
      val avgFee = AverageFees(1, 3, 5, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)), "example1")
      metadataOperations.runQuery(TezosDatabaseOperations.writeFees(List(avgFee))).isReadyWithin(5 seconds)

      sut.listAttributeValues("fees", "kind", None).futureValue.right.get shouldBe List("example1")
    }

    "returns a list of errors when asked for medium attribute of Fees without filter - numeric attributes should not be displayed" in {
      val avgFee = AverageFees(1, 3, 5, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)), "example1")

      dbHandler.run(TezosDatabaseOperations.writeFees(List(avgFee))).isReadyWithin(5 seconds)


      sut.listAttributeValues("fees", "medium", None).futureValue.left.get shouldBe List(InvalidAttributeDataType("medium"), HighCardinalityAttribute("medium"))

    }

    "return list with one error when the minimum matching length is greater than match length" in {
      val avgFee = AverageFees(1, 3, 5, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)), "example1")
      dbHandler.run(TezosDatabaseOperations.writeFees(List(avgFee))).isReadyWithin(5.seconds)

      sut.listAttributeValues("fees", "kind", Some("exa"), Some(AttributeCacheConfiguration(true, 4, 5))).futureValue.left.get shouldBe List(InvalidAttributeFilterLength("kind", 4))
    }

    "return empty list when trying to sql inject" in {
      val avgFee = AverageFees(1, 3, 5, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)), "example1")

      dbHandler.run(TezosDatabaseOperations.writeFees(List(avgFee))).isReadyWithin(5 seconds)
      // That's how the SLQ-injected string will look like:
      // SELECT DISTINCT kind FROM fees WHERE kind LIKE '%'; DELETE FROM fees WHERE kind LIKE '%'
      val maliciousFilter = Some("'; DELETE FROM fees WHERE kind LIKE '")


      sut.listAttributeValues("fees", "kind", maliciousFilter).futureValue.right.get shouldBe List.empty

      dbHandler.run(Tables.Fees.length.result).futureValue shouldBe 1

    }
    "correctly apply the filter" in {
      val avgFees = List(
        AverageFees(1, 3, 5, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)), "example1"),
        AverageFees(2, 4, 6, Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 31)), "example2")
      )

      sut.listAttributeValues("fees", "kind", Some("1")).futureValue.right.get shouldBe List.empty
      dbHandler.run(TezosDatabaseOperations.writeFees(avgFees)).isReadyWithin(5 seconds)

      sut.listAttributeValues("fees", "kind", None).futureValue.right.get should contain theSameElementsAs List("example1", "example2")
      sut.listAttributeValues("fees", "kind", Some("ex")).futureValue.right.get should contain theSameElementsAs List("example1", "example2")
      sut.listAttributeValues("fees", "kind", Some("ample")).futureValue.right.get should contain theSameElementsAs List("example1", "example2")
      sut.listAttributeValues("fees", "kind", Some("1")).futureValue.right.get shouldBe List("example1")

    }

    "should validate correctly fields" in {
      sut.isAttributeValid("fees", "low").futureValue shouldBe true
    }

    "should return false when there will be field not existing in the DB" in {
      sut.isAttributeValid("fees", "WRONG").futureValue shouldBe false
    }

  }

}
