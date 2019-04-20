package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.config.Platforms.{PlatformsConfiguration, TezosConfiguration, TezosNodeConfiguration}
import tech.cryptonomic.conseil.config.Types.PlatformName
import tech.cryptonomic.conseil.config.{MetadataOverridesConfiguration, PlatformConfiguration, Platforms}
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType.Int
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.KeyType.NonKey
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity}
import tech.cryptonomic.conseil.metadata.{MetadataService, UnitTransformation}
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.JsonUtil.toListOfMaps

import scala.concurrent.Future.successful

class PlatformDiscoveryTest extends WordSpec with Matchers with ScalatestRouteTest with MockFactory {

  "The platform discovery route" should {

    val tezosPlatformDiscoveryOperations = stub[TezosPlatformDiscoveryOperations]

    val sut = (metadataOverridesConfiguration: Map[PlatformName, PlatformConfiguration]) => PlatformDiscovery(new MetadataService(
      PlatformsConfiguration(Map(Platforms.Tezos -> List(
        TezosConfiguration("mainnet",
          TezosNodeConfiguration("tezos-host", 123, "https://"))))),
      new UnitTransformation(MetadataOverridesConfiguration(metadataOverridesConfiguration)),
      tezosPlatformDiscoveryOperations)).route

    "expose an endpoint to get the list of supported platforms" in {
      // when
      Get("/v2/metadata/platforms") ~> addHeader("apiKey", "hooman") ~> sut(Map.empty) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, String]] = toListOfMaps[String](responseAs[String])
        result.head("name") shouldBe "tezos"
        result.head("displayName") shouldBe "Tezos"
      }
    }

    "should filter out hidden platforms" in {
      // when
      Get("/v2/metadata/platforms") ~> addHeader("apiKey", "hooman") ~> sut(Map("tezos" -> PlatformConfiguration(None, Some(false)))) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, String]] = toListOfMaps[String](responseAs[String])
        result.size shouldBe 0
      }
    }

    "should rename platform's display name" in {
      // when
      Get("/v2/metadata/platforms") ~> addHeader("apiKey", "hooman") ~> sut(Map("tezos" -> PlatformConfiguration(Some("overwritten-name"), None))) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, String]] = toListOfMaps[String](responseAs[String])
        result.head("displayName") shouldBe "overwritten-name"
      }
    }

    "expose an endpoint to get the list of supported networks" in {
      // when
      Get("/v2/metadata/tezos/networks") ~> addHeader("apiKey", "hooman") ~> sut(Map.empty) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, String]] = toListOfMaps[String](responseAs[String])
        result.head("name") shouldBe "mainnet"
        result.head("displayName") shouldBe "Mainnet"
      }
    }

    "expose an endpoint to get the list of supported entities" in {
      // given
      (tezosPlatformDiscoveryOperations.getEntities _).when().returns(successful(List(Entity("entity", "entity-name", 1))))

      // when
      Get("/v2/metadata/tezos/mainnet/entities") ~> addHeader("apiKey", "hooman") ~> sut(Map.empty) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, String]] = toListOfMaps[String](responseAs[String])
        result.head("name") shouldBe "entity"
        result.head("displayName") shouldBe "entity-name"
        result.head("count") shouldBe "1"
      }
    }

    "expose an endpoint to get the list of supported attributes" in {
      // given
      (tezosPlatformDiscoveryOperations.getEntities _).when().returns(successful(List(Entity("entity", "entity-name", 1))))
      (tezosPlatformDiscoveryOperations.getTableAttributes _).when("entity").returns(successful(Some(List(Attribute("attribute", "attribute-name", Int, None, NonKey, "entity")))))

      // when
      Get("/v2/metadata/tezos/mainnet/entity/attributes") ~> addHeader("apiKey", "hooman") ~> sut(Map.empty) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, String]] = toListOfMaps[String](responseAs[String])
        result.head("name") shouldBe "attribute"
        result.head("displayName") shouldBe "attribute-name"
      }
    }
  }
}