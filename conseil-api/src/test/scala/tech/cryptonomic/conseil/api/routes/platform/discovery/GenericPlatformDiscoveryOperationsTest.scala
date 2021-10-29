package tech.cryptonomic.conseil.api.routes.platform.discovery

import java.sql.Timestamp
import java.time.LocalDateTime
import cats.effect.{ContextShift, IO}
import com.softwaremill.diffx.scalatest.DiffMatcher
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.IntegrationPatience
import slick.dbio
import tech.cryptonomic.conseil.api.metadata.AttributeValuesCacheConfiguration
import tech.cryptonomic.conseil.api.{BitcoinInMemoryDatabaseSetup, TezosInMemoryDatabaseSetup}
import tech.cryptonomic.conseil.common.cache.MetadataCaching
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{
  HighCardinalityAttribute,
  InvalidAttributeDataType,
  InvalidAttributeFilterLength
}
import tech.cryptonomic.conseil.common.generic.chain.DBIORunner
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Attribute, _}
import tech.cryptonomic.conseil.common.metadata._
import tech.cryptonomic.conseil.common.testkit.{ConseilSpec, InMemoryDatabase}
import tech.cryptonomic.conseil.common.tezos.{Fork, Tables => TezosT}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class GenericPlatformDiscoveryOperationsTest
    extends ConseilSpec
    with InMemoryDatabase
    with TezosInMemoryDatabaseSetup
    with BitcoinInMemoryDatabaseSetup
    with MockFactory
    with DiffMatcher
    with IntegrationPatience {

  import slick.jdbc.PostgresProfile.api._
  import tech.cryptonomic.conseil.common.config.Platforms._

  import scala.concurrent.ExecutionContext.Implicits.global

  val dbioRunner = new DBIORunner {
    override def runQuery[A](action: dbio.DBIO[A]): Future[A] = dbHandler.run(action)
  }
  val metadataOperations: Map[(String, String), DBIORunner] = Map(
    ("tezos", "alphanet") -> dbioRunner,
    ("bitcoin", "mainnet") -> dbioRunner
  )

  val dbCfg = ConfigFactory.parseString("""
    |    db {
    |      dataSourceClass: "org.postgresql.ds.PGSimpleDataSource"
    |      properties {
    |        user: "foo"
    |        password: "bar"
    |        url: "jdbc:postgresql://localhost:5432/postgres"
    |      }
    |      numThreads: 10
    |      maxConnections: 10
    |    }
        """.stripMargin)

  implicit val contextShift: ContextShift[IO] = IO.contextShift(implicitly[ExecutionContext])

  val metadataCaching: MetadataCaching[IO] = MetadataCaching.empty[IO].unsafeRunSync()
  val metadadataConfiguration: MetadataConfiguration = MetadataConfiguration(Map.empty)
  val cacheConfiguration = new AttributeValuesCacheConfiguration(metadadataConfiguration)
  val sut: GenericPlatformDiscoveryOperations =
    GenericPlatformDiscoveryOperations(metadataOperations, metadataCaching, cacheConfiguration, 10 seconds, 100)

  override def beforeAll(): Unit = {
    super.beforeAll()
    sut.init(
      List(
        (Platform("tezos", "Tezos"), Network("alphanet", "Alphanet", "tezos", "alphanet")),
        (Platform("bitcoin", "Bitcoin"), Network("mainnet", "Mainnet", "bitcoin", "mainnet"))
      )
    )
    ()
  }

  "getNetworks (all)" should {
      "return list with one element" in {
        val config = PlatformsConfiguration(
          List(
            TezosConfiguration(
              "alphanet",
              enabled = true,
              TezosNodeConfiguration(protocol = "http", hostname = "localhost", port = 8732),
              dbCfg,
              None
            )
          )
        )

        config.getNetworks("tezos") shouldBe List(Network("alphanet", "Alphanet", "tezos", "alphanet"))
      }

      "return two networks" in {
        val config = PlatformsConfiguration(
          List(
            TezosConfiguration(
              "alphanet",
              enabled = true,
              TezosNodeConfiguration(protocol = "http", hostname = "localhost", port = 8732),
              dbCfg,
              None
            ),
            TezosConfiguration(
              "alphanet-staging",
              enabled = true,
              TezosNodeConfiguration(
                protocol = "https",
                hostname = "nautilus.cryptonomic.tech",
                port = 8732,
                pathPrefix = "tezos/alphanet/"
              ),
              dbCfg,
              None
            )
          )
        )
        config.getNetworks("tezos") should have size 2
      }

      "return networks from two platforms" in {
        val config = PlatformsConfiguration(
          List(
            TezosConfiguration(
              "alphanet",
              enabled = true,
              TezosNodeConfiguration(protocol = "http", hostname = "localhost", port = 8732),
              dbCfg,
              None
            ),
            BitcoinConfiguration(
              "mainnet",
              enabled = true,
              BitcoinNodeConfiguration(
                hostname = "mainnet",
                port = 1,
                protocol = "https",
                username = "username",
                password = "password"
              ),
              dbCfg,
              BitcoinBatchFetchConfiguration(
                indexerThreadsCount = 1,
                httpFetchThreadsCount = 1,
                hashBatchSize = 1,
                blocksBatchSize = 1,
                transactionsBatchSize = 1
              )
            )
          )
        )

        config.getNetworks("tezos") shouldBe List(Network("alphanet", "Alphanet", "tezos", "alphanet"))
        config.getNetworks("bitcoin") shouldBe List(Network("mainnet", "Mainnet", "bitcoin", "mainnet"))
      }

    }

  "getEntities (tezos)" should {
      "return list of entities for tezos blockchain" in {
        sut.getEntities(NetworkPath("alphanet", PlatformPath("tezos"))).futureValue.toSet should matchTo(
          Set(
            Entity("big_maps", "Big maps", 0),
            Entity("operations", "Operations", 0),
            Entity("originated_account_maps", "Originated account maps", 0),
            Entity("fees", "Fees", 0),
            Entity("accounts_history", "Accounts history", 0),
            Entity("operation_groups", "Operation groups", 0),
            Entity("bakers", "Bakers", 0),
            Entity("bakers_checkpoint", "Bakers checkpoint", 0),
            Entity("accounts_checkpoint", "Accounts checkpoint", 0),
            Entity("accounts", "Accounts", 0),
            Entity("big_map_contents", "Big map contents", 0),
            Entity("balance_updates", "Balance updates", 0),
            Entity("processed_chain_events", "Processed chain events", 0),
            Entity("blocks", "Blocks", 0)
          )
        )
      }
    }

  "getEntities (bitcoin)" should {
      "return list of entities for bitcoin blockchain" in {
        sut.getEntities(NetworkPath("mainnet", PlatformPath("bitcoin"))).futureValue.toSet should matchTo(
          Set(
            Entity("transactions", "Transactions", 0),
            Entity("blocks", "Blocks", 0),
            Entity("inputs", "Inputs", 0),
            Entity("outputs", "Outputs", 0),
            Entity("accounts", "Accounts", 0)
          )
        )
      }
    }

  "getTableAttributes (tezos)" should {
      val networkPath = NetworkPath("alphanet", PlatformPath("tezos"))
      "return list of attributes of Fees" in {

        sut.getTableAttributes(EntityPath("fees", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("low", "Low", DataType.Int, None, KeyType.NonKey, "fees"),
            Attribute("medium", "Medium", DataType.Int, None, KeyType.NonKey, "fees"),
            Attribute("high", "High", DataType.Int, None, KeyType.NonKey, "fees"),
            Attribute("timestamp", "Timestamp", DataType.DateTime, None, KeyType.NonKey, "fees"),
            Attribute("kind", "Kind", DataType.String, None, KeyType.NonKey, "fees"),
            Attribute("cycle", "Cycle", DataType.Int, None, KeyType.NonKey, "fees"),
            Attribute("level", "Level", DataType.LargeInt, None, KeyType.NonKey, "fees"),
            Attribute("invalidated_asof", "Invalidated asof", DataType.DateTime, None, KeyType.NonKey, "fees"),
            Attribute("fork_id", "Fork id", DataType.String, None, KeyType.NonKey, "fees")
          )
        )
      }

      "return list of attributes of accounts" in {
        sut.getTableAttributes(EntityPath("accounts", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("account_id", "Account id", DataType.String, None, KeyType.UniqueKey, "accounts"),
            Attribute("block_id", "Block id", DataType.String, None, KeyType.UniqueKey, "accounts"),
            Attribute("counter", "Counter", DataType.Int, None, KeyType.NonKey, "accounts"),
            Attribute("script", "Script", DataType.String, None, KeyType.NonKey, "accounts"),
            Attribute("storage", "Storage", DataType.String, None, KeyType.NonKey, "accounts"),
            Attribute("balance", "Balance", DataType.Decimal, None, KeyType.NonKey, "accounts"),
            Attribute("block_level", "Block level", DataType.LargeInt, None, KeyType.UniqueKey, "accounts"),
            Attribute("manager", "Manager", DataType.String, None, KeyType.UniqueKey, "accounts"),
            Attribute("spendable", "Spendable", DataType.Boolean, None, KeyType.NonKey, "accounts"),
            Attribute("delegate_setable", "Delegate setable", DataType.Boolean, None, KeyType.NonKey, "accounts"),
            Attribute("delegate_value", "Delegate value", DataType.String, None, KeyType.NonKey, "accounts"),
            Attribute("is_baker", "Is baker", DataType.Boolean, None, KeyType.NonKey, "accounts"),
            Attribute("is_activated", "Is activated", DataType.Boolean, None, KeyType.UniqueKey, "accounts"),
            Attribute("invalidated_asof", "Invalidated asof", DataType.DateTime, None, KeyType.NonKey, "accounts"),
            Attribute("fork_id", "Fork id", DataType.String, None, KeyType.UniqueKey, "accounts"),
            Attribute("script_hash", "Script hash", DataType.String, None, KeyType.NonKey, "accounts")
          )
        )
      }

      "return list of attributes of blocks" in {
        sut.getTableAttributes(EntityPath("blocks", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("level", "Level", DataType.LargeInt, None, KeyType.UniqueKey, "blocks"),
            Attribute("proto", "Proto", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("predecessor", "Predecessor", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("timestamp", "Timestamp", DataType.DateTime, None, KeyType.NonKey, "blocks"),
            Attribute("fitness", "Fitness", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("context", "Context", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("signature", "Signature", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("protocol", "Protocol", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("chain_id", "Chain id", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("hash", "Hash", DataType.String, None, KeyType.UniqueKey, "blocks"),
            Attribute("operations_hash", "Operations hash", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("period_kind", "Period kind", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute(
              "current_expected_quorum",
              "Current expected quorum",
              DataType.Int,
              None,
              KeyType.NonKey,
              "blocks"
            ),
            Attribute("active_proposal", "Active proposal", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("baker", "Baker", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("consumed_gas", "Consumed gas", DataType.Decimal, None, KeyType.NonKey, "blocks"),
            Attribute("meta_level", "Meta level", DataType.LargeInt, None, KeyType.NonKey, "blocks"),
            Attribute("meta_level_position", "Meta level position", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("meta_cycle", "Meta cycle", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("meta_cycle_position", "Meta cycle position", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("meta_voting_period", "Meta voting period", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute(
              "meta_voting_period_position",
              "Meta voting period position",
              DataType.Int,
              None,
              KeyType.NonKey,
              "blocks"
            ),
            Attribute("priority", "Priority", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("utc_year", "Utc year", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("utc_month", "Utc month", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("utc_day", "Utc day", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("utc_time", "Utc time", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("invalidated_asof", "Invalidated asof", DataType.DateTime, None, KeyType.NonKey, "blocks"),
            Attribute("fork_id", "Fork id", DataType.String, None, KeyType.UniqueKey, "blocks")
          )
        )

      }

      "return list of attributes of operations" in {
        sut.getTableAttributes(EntityPath("operations", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("operation_id", "Operation id", DataType.Int, None, KeyType.UniqueKey, "operations"),
            Attribute(
              "operation_group_hash",
              "Operation group hash",
              DataType.String,
              None,
              KeyType.UniqueKey,
              "operations"
            ),
            Attribute("kind", "Kind", DataType.String, None, KeyType.UniqueKey, "operations"),
            Attribute("level", "Level", DataType.LargeInt, None, KeyType.UniqueKey, "operations"),
            Attribute("delegate", "Delegate", DataType.String, None, KeyType.UniqueKey, "operations"),
            Attribute("slots", "Slots", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("nonce", "Nonce", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("operation_order", "Operation order", DataType.Int, None, KeyType.NonKey, "operations"),
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
            Attribute(
              "parameters_micheline",
              "Parameters micheline",
              DataType.String,
              None,
              KeyType.NonKey,
              "operations"
            ),
            Attribute(
              "parameters_entrypoints",
              "Parameters entrypoints",
              DataType.String,
              None,
              KeyType.NonKey,
              "operations"
            ),
            Attribute("manager_pubkey", "Manager pubkey", DataType.String, None, KeyType.UniqueKey, "operations"),
            Attribute("balance", "Balance", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("spendable", "Spendable", DataType.Boolean, None, KeyType.NonKey, "operations"),
            Attribute("delegatable", "Delegatable", DataType.Boolean, None, KeyType.NonKey, "operations"),
            Attribute("script", "Script", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("storage", "Storage", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("storage_micheline", "Storage micheline", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("status", "Status", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("consumed_gas", "Consumed gas", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute("storage_size", "Storage size", DataType.Decimal, None, KeyType.NonKey, "operations"),
            Attribute(
              "paid_storage_size_diff",
              "Paid storage size diff",
              DataType.Decimal,
              None,
              KeyType.NonKey,
              "operations"
            ),
            Attribute(
              "originated_contracts",
              "Originated contracts",
              DataType.String,
              None,
              KeyType.UniqueKey,
              "operations"
            ),
            Attribute("block_hash", "Block hash", DataType.String, None, KeyType.UniqueKey, "operations"),
            Attribute("block_level", "Block level", DataType.LargeInt, None, KeyType.UniqueKey, "operations"),
            Attribute("ballot", "Ballot", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("internal", "Internal", DataType.Boolean, None, KeyType.NonKey, "operations"),
            Attribute("timestamp", "Timestamp", DataType.DateTime, None, KeyType.UniqueKey, "operations"),
            Attribute("proposal", "Proposal", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("cycle", "Cycle", DataType.Int, None, KeyType.UniqueKey, "operations"),
            Attribute("branch", "Branch", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("number_of_slots", "Number of slots", DataType.Int, None, KeyType.NonKey, "operations"),
            Attribute("period", "Period", DataType.Int, None, KeyType.NonKey, "operations"),
            Attribute("ballot_period", "Ballot period", DataType.Int, None, KeyType.NonKey, "operations"),
            Attribute("errors", "Errors", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("utc_year", "Utc year", DataType.Int, None, KeyType.NonKey, "operations"),
            Attribute("utc_month", "Utc month", DataType.Int, None, KeyType.NonKey, "operations"),
            Attribute("utc_day", "Utc day", DataType.Int, None, KeyType.NonKey, "operations"),
            Attribute("utc_time", "Utc time", DataType.String, None, KeyType.NonKey, "operations"),
            Attribute("invalidated_asof", "Invalidated asof", DataType.DateTime, None, KeyType.NonKey, "operations"),
            Attribute("fork_id", "Fork id", DataType.String, None, KeyType.UniqueKey, "operations"),
            Attribute("invalidated_asof", "Invalidated asof", DataType.DateTime, None, KeyType.NonKey, "operations"),
            Attribute("fork_id", "Fork id", DataType.String, None, KeyType.UniqueKey, "operations")
          )
        )
      }

      "return list of attributes of operation groups" in {

        sut.getTableAttributes(EntityPath("operation_groups", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("protocol", "Protocol", DataType.String, None, KeyType.NonKey, "operation_groups"),
            Attribute("chain_id", "Chain id", DataType.String, None, KeyType.NonKey, "operation_groups"),
            Attribute("hash", "Hash", DataType.String, None, KeyType.UniqueKey, "operation_groups"),
            Attribute("branch", "Branch", DataType.String, None, KeyType.NonKey, "operation_groups"),
            Attribute("signature", "Signature", DataType.String, None, KeyType.NonKey, "operation_groups"),
            Attribute("block_id", "Block id", DataType.String, None, KeyType.UniqueKey, "operation_groups"),
            Attribute("block_level", "Block level", DataType.LargeInt, None, KeyType.UniqueKey, "operation_groups"),
            Attribute(
              "invalidated_asof",
              "Invalidated asof",
              DataType.DateTime,
              None,
              KeyType.NonKey,
              "operation_groups"
            ),
            Attribute("fork_id", "Fork id", DataType.String, None, KeyType.UniqueKey, "operation_groups")
          )
        )
      }

      "return list of attributes of bakers" in {

        sut.getTableAttributes(EntityPath("bakers", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("pkh", "Pkh", DataType.String, None, KeyType.UniqueKey, "bakers"),
            Attribute("block_id", "Block id", DataType.String, None, KeyType.NonKey, "bakers"),
            Attribute("balance", "Balance", DataType.Decimal, None, KeyType.NonKey, "bakers"),
            Attribute("frozen_balance", "Frozen balance", DataType.Decimal, None, KeyType.NonKey, "bakers"),
            Attribute("staking_balance", "Staking balance", DataType.Decimal, None, KeyType.NonKey, "bakers"),
            Attribute("delegated_balance", "Delegated balance", DataType.Decimal, None, KeyType.NonKey, "bakers"),
            Attribute("rolls", "Rolls", DataType.Int, None, KeyType.NonKey, "bakers"),
            Attribute("deactivated", "Deactivated", DataType.Boolean, None, KeyType.NonKey, "bakers"),
            Attribute("grace_period", "Grace period", DataType.Int, None, KeyType.NonKey, "bakers"),
            Attribute("block_level", "Block level", DataType.LargeInt, None, KeyType.NonKey, "bakers"),
            Attribute("cycle", "Cycle", DataType.Int, None, KeyType.NonKey, "bakers"),
            Attribute("period", "Period", DataType.Int, None, KeyType.NonKey, "bakers"),
            Attribute(
              "invalidated_asof",
              "Invalidated asof",
              DataType.DateTime,
              None,
              KeyType.NonKey,
              "bakers"
            ),
            Attribute("fork_id", "Fork id", DataType.String, None, KeyType.UniqueKey, "bakers")
          )
        )
      }

      "return list of attributes of big maps" in {

        sut.getTableAttributes(EntityPath("big_maps", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("big_map_id", "Big map id", DataType.Decimal, None, KeyType.UniqueKey, "big_maps"),
            Attribute("key_type", "Key type", DataType.String, None, KeyType.NonKey, "big_maps"),
            Attribute("value_type", "Value type", DataType.String, None, KeyType.NonKey, "big_maps"),
            Attribute("fork_id", "Fork id", DataType.String, None, KeyType.UniqueKey, "big_maps"),
            Attribute("block_level", "Block level", DataType.LargeInt, None, KeyType.NonKey, "big_maps"),
            Attribute("invalidated_asof", "Invalidated asof", DataType.DateTime, None, KeyType.NonKey, "big_maps")
          )
        )
      }

      "return list of attributes of big map contents" in {

        sut.getTableAttributes(EntityPath("big_map_contents", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("big_map_id", "Big map id", DataType.Decimal, None, KeyType.UniqueKey, "big_map_contents"),
            Attribute("key", "Key", DataType.String, None, KeyType.UniqueKey, "big_map_contents"),
            Attribute("key_hash", "Key hash", DataType.String, None, KeyType.NonKey, "big_map_contents"),
            Attribute(
              "operation_group_id",
              "Operation group id",
              DataType.String,
              None,
              KeyType.UniqueKey,
              "big_map_contents"
            ),
            Attribute("value", "Value", DataType.String, None, KeyType.NonKey, "big_map_contents"),
            Attribute("value_micheline", "Value micheline", DataType.String, None, KeyType.NonKey, "big_map_contents"),
            Attribute("block_level", "Block level", DataType.LargeInt, None, KeyType.NonKey, "big_map_contents"),
            Attribute("timestamp", "Timestamp", DataType.DateTime, None, KeyType.NonKey, "big_map_contents"),
            Attribute("cycle", "Cycle", DataType.Int, None, KeyType.NonKey, "big_map_contents"),
            Attribute("period", "Period", DataType.Int, None, KeyType.NonKey, "big_map_contents"),
            Attribute("fork_id", "Fork id", DataType.String, None, KeyType.UniqueKey, "big_map_contents"),
            Attribute("invalidated_asof", "Invalidated asof", DataType.DateTime, None, KeyType.NonKey, "big_map_contents")
          )
        )
      }

      "return list of attributes of originated account maps" in {

        sut
          .getTableAttributes(EntityPath("originated_account_maps", networkPath))
          .futureValue
          .value
          .toSet should matchTo(
          Set(
            Attribute("big_map_id", "Big map id", DataType.Decimal, None, KeyType.UniqueKey, "originated_account_maps"),
            Attribute("account_id", "Account id", DataType.String, None, KeyType.UniqueKey, "originated_account_maps")
          )
        )
      }

      "return empty list for non existing table" in {
        sut.getTableAttributes(EntityPath("nonExisting", networkPath)).futureValue shouldBe None
      }
    }

  "getTableAttributes (bitcoin)" should {
      val networkPath = NetworkPath("mainnet", PlatformPath("bitcoin"))

      "return list of attributes of blocks" in {
        sut.getTableAttributes(EntityPath("blocks", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("hash", "Hash", DataType.String, None, KeyType.UniqueKey, "blocks"),
            Attribute("size", "Size", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("stripped_size", "Stripped size", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("weight", "Weight", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("level", "Level", DataType.Int, None, KeyType.UniqueKey, "blocks"),
            Attribute("version", "Version", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("version_hex", "Version hex", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("merkle_root", "Merkle root", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("time", "Time", DataType.DateTime, None, KeyType.UniqueKey, "blocks"),
            Attribute("median_time", "Median time", DataType.DateTime, None, KeyType.NonKey, "blocks"),
            Attribute("nonce", "Nonce", DataType.LargeInt, None, KeyType.NonKey, "blocks"),
            Attribute("bits", "Bits", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("difficulty", "Difficulty", DataType.Decimal, None, KeyType.NonKey, "blocks"),
            Attribute("chain_work", "Chain work", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("n_tx", "N tx", DataType.Int, None, KeyType.NonKey, "blocks"),
            Attribute("previous_block_hash", "Previous block hash", DataType.String, None, KeyType.NonKey, "blocks"),
            Attribute("next_block_hash", "Next block hash", DataType.String, None, KeyType.NonKey, "blocks")
          )
        )
      }

      "return list of attributes of transactions" in {
        sut.getTableAttributes(EntityPath("transactions", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("txid", "Txid", DataType.String, None, KeyType.UniqueKey, "transactions"),
            Attribute("lock_time", "Lock time", DataType.DateTime, None, KeyType.NonKey, "transactions"),
            Attribute("block_time", "Block time", DataType.DateTime, None, KeyType.NonKey, "transactions"),
            Attribute("weight", "Weight", DataType.Int, None, KeyType.NonKey, "transactions"),
            Attribute("hash", "Hash", DataType.String, None, KeyType.NonKey, "transactions"),
            Attribute("hex", "Hex", DataType.String, None, KeyType.NonKey, "transactions"),
            Attribute("version", "Version", DataType.LargeInt, None, KeyType.NonKey, "transactions"),
            Attribute("block_hash", "Block hash", DataType.String, None, KeyType.NonKey, "transactions"),
            Attribute("size", "Size", DataType.Int, None, KeyType.NonKey, "transactions"),
            Attribute("vsize", "Vsize", DataType.Int, None, KeyType.NonKey, "transactions"),
            Attribute("block_level", "Block level", DataType.Int, None, KeyType.UniqueKey, "transactions")
          )
        )
      }

      "return list of attributes of inputs" in {
        sut.getTableAttributes(EntityPath("inputs", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("txid", "Txid", DataType.String, None, KeyType.NonKey, "inputs"),
            Attribute("output_txid", "Output txid", DataType.String, None, KeyType.NonKey, "inputs"),
            Attribute("v_out", "V out", DataType.Int, None, KeyType.NonKey, "inputs"),
            Attribute("script_sig_asm", "Script sig asm", DataType.String, None, KeyType.NonKey, "inputs"),
            Attribute("script_sig_hex", "Script sig hex", DataType.String, None, KeyType.NonKey, "inputs"),
            Attribute("sequence", "Sequence", DataType.LargeInt, None, KeyType.NonKey, "inputs"),
            Attribute("coinbase", "Coinbase", DataType.String, None, KeyType.NonKey, "inputs"),
            Attribute("tx_in_witness", "Tx in witness", DataType.String, None, KeyType.NonKey, "inputs"),
            Attribute("block_hash", "Block hash", DataType.String, None, KeyType.UniqueKey, "inputs"),
            Attribute("block_level", "Block level", DataType.Int, None, KeyType.UniqueKey, "inputs"),
            Attribute("block_time", "Block time", DataType.DateTime, None, KeyType.NonKey, "inputs")
          )
        )
      }

      "return list of attributes of outputs" in {
        sut.getTableAttributes(EntityPath("outputs", networkPath)).futureValue.value.toSet should matchTo(
          Set(
            Attribute("txid", "Txid", DataType.String, None, KeyType.NonKey, "outputs"),
            Attribute("value", "Value", DataType.Decimal, None, KeyType.NonKey, "outputs"),
            Attribute("n", "N", DataType.Int, None, KeyType.NonKey, "outputs"),
            Attribute("script_pub_key_asm", "Script pub key asm", DataType.String, None, KeyType.NonKey, "outputs"),
            Attribute("script_pub_key_hex", "Script pub key hex", DataType.String, None, KeyType.NonKey, "outputs"),
            Attribute(
              "script_pub_key_req_sigs",
              "Script pub key req sigs",
              DataType.Int,
              None,
              KeyType.NonKey,
              "outputs"
            ),
            Attribute("script_pub_key_type", "Script pub key type", DataType.String, None, KeyType.NonKey, "outputs"),
            Attribute(
              "script_pub_key_addresses",
              "Script pub key addresses",
              DataType.String,
              None,
              KeyType.NonKey,
              "outputs"
            ),
            Attribute("block_hash", "Block hash", DataType.String, None, KeyType.UniqueKey, "outputs"),
            Attribute("block_level", "Block level", DataType.Int, None, KeyType.UniqueKey, "outputs"),
            Attribute("block_time", "Block time", DataType.DateTime, None, KeyType.NonKey, "outputs")
          )
        )
      }
    }

  "listAttributeValues (tezos)" should {
      val networkPath = NetworkPath("alphanet", PlatformPath("tezos"))

      "return list of values of kind attribute of Fees without filter" in {
        val fee =
          TezosT.FeesRow(
            1,
            3,
            5,
            Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)),
            "example1",
            None,
            None,
            forkId = Fork.mainForkId
          )
        metadataOperations(("tezos", "alphanet")).runQuery(TezosT.Fees ++= List(fee)).isReadyWithin(5 seconds)

        sut
          .listAttributeValues(AttributePath("kind", EntityPath("fees", networkPath)), None)
          .futureValue
          .right
          .get shouldBe List("example1")
      }

      "returns a list of errors when asked for medium attribute of Fees without filter - numeric attributes should not be displayed" in {
        val fee =
          TezosT.FeesRow(
            1,
            3,
            5,
            Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)),
            "example1",
            None,
            None,
            forkId = Fork.mainForkId
          )

        dbHandler.run(TezosT.Fees ++= List(fee)).isReadyWithin(5 seconds)

        sut
          .listAttributeValues(AttributePath("medium", EntityPath("fees", networkPath)), None)
          .futureValue
          .left
          .get shouldBe List(
          InvalidAttributeDataType("medium"),
          HighCardinalityAttribute("medium")
        )

      }

      "return list with one error when the minimum matching length is greater than match length" in {
        val fee =
          TezosT.FeesRow(
            1,
            3,
            5,
            Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)),
            "example1",
            None,
            None,
            forkId = Fork.mainForkId
          )

        dbHandler.run(TezosT.Fees ++= List(fee)).isReadyWithin(5.seconds)

        sut
          .listAttributeValues(
            AttributePath("kind", EntityPath("fees", networkPath)),
            Some("exa"),
            Some(AttributeCacheConfiguration(cached = true, 4, 5))
          )
          .futureValue
          .left
          .get shouldBe List(InvalidAttributeFilterLength("kind", 4))
      }

      "return empty list when trying to sql inject" in {
        val fee =
          TezosT.FeesRow(
            1,
            3,
            5,
            Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)),
            "example1",
            None,
            None,
            forkId = Fork.mainForkId
          )

        dbHandler.run(TezosT.Fees ++= List(fee)).isReadyWithin(5 seconds)
        // That's how the SLQ-injected string will look like:
        // SELECT DISTINCT kind FROM fees WHERE kind LIKE '%'; DELETE FROM fees WHERE kind LIKE '%'
        val maliciousFilter = Some("'; DELETE FROM fees WHERE kind LIKE '")

        sut
          .listAttributeValues(AttributePath("kind", EntityPath("fees", networkPath)), maliciousFilter)
          .futureValue
          .right
          .get shouldBe List.empty

        dbHandler.run(TezosT.Fees.length.result).futureValue shouldBe 1

      }
      "correctly apply the filter" in {
        val fees = List(
          TezosT.FeesRow(
            1,
            3,
            5,
            Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)),
            "example1",
            None,
            None,
            forkId = Fork.mainForkId
          ),
          TezosT.FeesRow(
            2,
            4,
            6,
            Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 31)),
            "example2",
            None,
            None,
            forkId = Fork.mainForkId
          )
        )

        sut
          .listAttributeValues(AttributePath("kind", EntityPath("fees", networkPath)), Some("1"))
          .futureValue
          .right
          .get shouldBe List.empty
        dbHandler.run(TezosT.Fees ++= fees).isReadyWithin(5 seconds)

        sut
          .listAttributeValues(AttributePath("kind", EntityPath("fees", networkPath)), None)
          .futureValue
          .right
          .get should contain theSameElementsAs List(
          "example1",
          "example2"
        )
        sut
          .listAttributeValues(AttributePath("kind", EntityPath("fees", networkPath)), Some("ex"))
          .futureValue
          .right
          .get should contain theSameElementsAs List(
          "example1",
          "example2"
        )
        sut
          .listAttributeValues(AttributePath("kind", EntityPath("fees", networkPath)), Some("ample"))
          .futureValue
          .right
          .get should contain theSameElementsAs List("example1", "example2")
        sut
          .listAttributeValues(AttributePath("kind", EntityPath("fees", networkPath)), Some("1"))
          .futureValue
          .right
          .get shouldBe List("example1")

      }
    }

}
