package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes.Network

class PlatformDiscoveryOperationsTest
  extends WordSpec
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

      PlatformDiscoveryOperations.getNetworks(cfg) should be (List(Network("alphanet","Alphanet","tezos","alphanet")))
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

      PlatformDiscoveryOperations.getNetworks(cfg).size should be (2)
    }
  }

}
