package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes.{Attributes, DataType, KeyType, Network}

class PlatformDiscoveryOperationsTest
  extends WordSpec
    with InMemoryDatabase
    with MockFactory
    with Matchers
    with ScalaFutures
    with OptionValues
    with LazyLogging {

  "getNetworks" should {
    "return list with one element" in {
      val cfg = ConfigFactory.parseString(
        """
          | platforms.tezos : {
          |  alphanet: {
          |    node: {
          |      protocol: "http",
          |      hostname: "localhost",
          |      port: 8732
          |      pathPrefix: ""
          |    }
          |  }
          | }
        """.stripMargin)

      PlatformDiscoveryOperations.getNetworks(cfg) shouldBe List(Network("alphanet", "Alphanet", "tezos", "alphanet"))
    }
    "return two networks" in {
      val cfg = ConfigFactory.parseString(
        """
          |platforms.tezos : {
          |  alphanet: {
          |    node: {
          |      protocol: "http",
          |      hostname: "localhost",
          |      port: 8732
          |      pathPrefix: ""
          |    }
          |  }
          |  alphanet-staging : {
          |    node: {
          |      protocol: "https"
          |      hostname: "nautilus.cryptonomic.tech",
          |      port: 8732
          |      pathPrefix: "tezos/alphanet/"
          |    }
          |  }
          |}
        """.stripMargin)

      PlatformDiscoveryOperations.getNetworks(cfg).size shouldBe 2
    }
  }

  "getEntities" should {

    import slick.jdbc.H2Profile.api._

    import scala.concurrent.ExecutionContext.Implicits.global

    "return list of attributes of Fees" in {

      dbHandler.run {
        DBIO.sequence(PlatformDiscoveryOperations.makeAttributesList("fees"))
      }.futureValue shouldBe
        List(
          Attributes("low", "Low", DataType.Int, 0, KeyType.UniqueKey, "fees"),
          Attributes("medium", "Medium", DataType.Int, 0, KeyType.UniqueKey, "fees"),
          Attributes("high", "High", DataType.Int, 0, KeyType.UniqueKey, "fees"),
          Attributes("timestamp", "Timestamp", DataType.DateTime, 0, KeyType.UniqueKey, "fees"),
          Attributes("kind", "Kind", DataType.String, 0, KeyType.UniqueKey, "fees")
        )
    }

    "return list of attributes of accounts" in {
      dbHandler.run {
        DBIO.sequence(PlatformDiscoveryOperations.makeAttributesList("accounts"))
      }.futureValue shouldBe
        List(
          Attributes("account_id", "Account id", DataType.String, 0, KeyType.UniqueKey, "accounts"),
          Attributes("block_id", "Block id", DataType.String, 0, KeyType.UniqueKey, "accounts"),
          Attributes("manager", "Manager", DataType.String, 0, KeyType.UniqueKey, "accounts"),
          Attributes("spendable", "Spendable", DataType.Boolean, 0, KeyType.UniqueKey, "accounts"),
          Attributes("delegate_setable", "Delegate setable", DataType.Boolean, 0, KeyType.UniqueKey, "accounts"),
          Attributes("delegate_value", "Delegate value", DataType.String, 0, KeyType.UniqueKey, "accounts"),
          Attributes("counter", "Counter", DataType.Int, 0, KeyType.UniqueKey, "accounts"),
          Attributes("script", "Script", DataType.String, 0, KeyType.UniqueKey, "accounts"),
          Attributes("balance", "Balance", DataType.Decimal, 0, KeyType.UniqueKey, "accounts"),
          Attributes("block_level", "Block level", DataType.Decimal, 0, KeyType.UniqueKey, "accounts")
        )
    }

    "return list of attributes of blocks" in {
      dbHandler.run {
        DBIO.sequence(PlatformDiscoveryOperations.makeAttributesList("blocks"))
      }.futureValue shouldBe
        List(
          Attributes("level", "Level", DataType.Int, 0, KeyType.UniqueKey, "blocks"),
          Attributes("proto", "Proto", DataType.Int, 0, KeyType.UniqueKey, "blocks"),
          Attributes("predecessor", "Predecessor", DataType.String, 0, KeyType.UniqueKey, "blocks"),
          Attributes("timestamp", "Timestamp", DataType.DateTime, 0, KeyType.UniqueKey, "blocks"),
          Attributes("validation_pass", "Validation pass", DataType.Int, 0, KeyType.UniqueKey, "blocks"),
          Attributes("fitness", "Fitness", DataType.String, 0, KeyType.UniqueKey, "blocks"),
          Attributes("context", "Context", DataType.String, 0, KeyType.UniqueKey, "blocks"),
          Attributes("signature", "Signature", DataType.String, 0, KeyType.UniqueKey, "blocks"),
          Attributes("protocol", "Protocol", DataType.String, 0, KeyType.UniqueKey, "blocks"),
          Attributes("chain_id", "Chain id", DataType.String, 0, KeyType.UniqueKey, "blocks"),
          Attributes("hash", "Hash", DataType.String, 0, KeyType.UniqueKey, "blocks"),
          Attributes("operations_hash", "Operations hash", DataType.String, 0, KeyType.UniqueKey, "blocks")
        )
    }

    "return list of attributes of operations" in {
      dbHandler.run {
        DBIO.sequence(PlatformDiscoveryOperations.makeAttributesList("operations"))
      }.futureValue shouldBe
        List(
          Attributes("kind", "Kind", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("source", "Source", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("amount", "Amount", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("destination", "Destination", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("balance", "Balance", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("delegate", "Delegate", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("operation_group_hash", "Operation group hash", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("operation_id", "Operation id", DataType.Int, 0, KeyType.UniqueKey, "operations"),
          Attributes("fee", "Fee", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("storage_limit", "Storage limit", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("gas_limit", "Gas limit", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("block_hash", "Block hash", DataType.String, 0, KeyType.UniqueKey, "operations"),
          Attributes("timestamp", "Timestamp", DataType.DateTime, 0, KeyType.UniqueKey, "operations"),
          Attributes("block_level", "Block level", DataType.Int, 0, KeyType.UniqueKey, "operations"),
          Attributes("pkh", "Pkh", DataType.String, 0, KeyType.UniqueKey, "operations")
        )
    }

    "return list of attributes of operation groups" in {
      dbHandler.run {
        DBIO.sequence(PlatformDiscoveryOperations.makeAttributesList("operation_groups"))
      }.futureValue shouldBe
        List(
          Attributes("protocol", "Protocol", DataType.String, 0, KeyType.UniqueKey, "operation_groups"),
          Attributes("chain_id", "Chain id", DataType.String, 0, KeyType.UniqueKey, "operation_groups"),
          Attributes("hash", "Hash", DataType.String, 0, KeyType.UniqueKey, "operation_groups"),
          Attributes("branch", "Branch", DataType.String, 0, KeyType.UniqueKey, "operation_groups"),
          Attributes("signature", "Signature", DataType.String, 0, KeyType.UniqueKey, "operation_groups"),
          Attributes("block_id", "Block id", DataType.String, 0, KeyType.UniqueKey, "operation_groups")
        )
    }

    "return empty list for non existing table" in {
      dbHandler.run {
        DBIO.sequence(PlatformDiscoveryOperations.makeAttributesList("nonExisting"))
      }.futureValue shouldBe List.empty
    }
  }

}
