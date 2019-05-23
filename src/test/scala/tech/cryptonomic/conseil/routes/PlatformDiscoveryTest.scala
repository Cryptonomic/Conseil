package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.config.Platforms.{PlatformsConfiguration, TezosConfiguration, TezosNodeConfiguration}
import tech.cryptonomic.conseil.config.Types.PlatformName
import tech.cryptonomic.conseil.config.{AttributeConfiguration, EntityConfiguration, MetadataOverridesConfiguration, NetworkConfiguration, PlatformConfiguration, Platforms}
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
      // given
      val matadataOverridesConfiguration = Map("tezos" -> PlatformConfiguration(None, Some(true)))

      // when
      Get("/v2/metadata/platforms") ~> addHeader("apiKey", "hooman") ~> sut(matadataOverridesConfiguration) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, String]] = toListOfMaps[String](responseAs[String])
        result.head("name") shouldBe "tezos"
        result.head("displayName") shouldBe "Tezos"
      }
    }

    "should filter out hidden platforms" in {
      // given
      val overridesConfiguration = Map("tezos" -> PlatformConfiguration(None, Some(false)))

      // when
      Get("/v2/metadata/platforms") ~> addHeader("apiKey", "hooman") ~> sut(overridesConfiguration) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, String]] = toListOfMaps[String](responseAs[String])
        result.size shouldBe 0
      }
    }

    "should rename platform's display name and description" in {
      // given
      val overridesConfiguration = Map("tezos" -> PlatformConfiguration(Some("overwritten-name"), Some(true), Some("description")))

      // when
      Get("/v2/metadata/platforms") ~> addHeader("apiKey", "hooman") ~> sut(overridesConfiguration) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, String]] = toListOfMaps[String](responseAs[String])
        result.head("displayName") shouldBe "overwritten-name"
        result.head("description") shouldBe "description"
      }
    }

    "expose an endpoint to get the list of supported networks" in {
      // given
      val overridesConfiguration = Map("tezos" ->
        PlatformConfiguration(None, Some(true), None, Map("mainnet" ->
          NetworkConfiguration(None, Some(true)))))

      // when
      Get("/v2/metadata/tezos/networks") ~> addHeader("apiKey", "hooman") ~> sut(overridesConfiguration) ~> check {

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

      val overridesConfiguration = Map("tezos" ->
        PlatformConfiguration(None, Some(true), None, Map("mainnet" ->
          NetworkConfiguration(None, Some(true), None, Map("entity" ->
            EntityConfiguration(None, Some(true)))))))

      // when
      Get("/v2/metadata/tezos/mainnet/entities") ~> addHeader("apiKey", "hooman") ~> sut(overridesConfiguration) ~> check {

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

      val overridesConfiguration = Map("tezos" ->
        PlatformConfiguration(None, Some(true), None, Map("mainnet" ->
          NetworkConfiguration(None, Some(true), None, Map("entity" ->
            EntityConfiguration(None, Some(true), None, Map("attribute" ->
              AttributeConfiguration(None, Some(true)))))))))

      // when
      Get("/v2/metadata/tezos/mainnet/entity/attributes") ~> addHeader("apiKey", "hooman") ~> sut(overridesConfiguration) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, String]] = toListOfMaps[String](responseAs[String])
        result.head("name") shouldBe "attribute"
        result.head("displayName") shouldBe "attribute-name"
      }
    }

    "override additional data for attributes" in {
      // given
      (tezosPlatformDiscoveryOperations.getEntities _).when().returns(successful(List(Entity("entity", "entity-name", 1))))
      (tezosPlatformDiscoveryOperations.getTableAttributes _).when("entity").returns(successful(Some(List(Attribute("attribute", "attribute-name", Int, None, NonKey, "entity")))))

      val overridesConfiguration = Map("tezos" ->
        PlatformConfiguration(None, Some(true), None, Map("mainnet" ->
          NetworkConfiguration(None, Some(true), None, Map("entity" ->
            EntityConfiguration(None, Some(true), None, Map("attribute" ->
              AttributeConfiguration(
                displayName = None,
                visible = Some(true),
                description = Some("description"),
                placeholder = Some("placeholder"),
                scale = Some(6),
                dataType = Some("hash"),
                dataFormat = Some("dataFormat"),
                valueMap = Some(Map("0" -> "value"))))))))))

      // when
      Get("/v2/metadata/tezos/mainnet/entity/attributes") ~> addHeader("apiKey", "hooman") ~> sut(overridesConfiguration) ~> check {

        // then
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        val result: List[Map[String, Any]] = toListOfMaps[Any](responseAs[String])
        result.head("name") shouldBe "attribute"
        result.head("displayName") shouldBe "attribute-name"
        result.head("description") shouldBe "description"
        result.head("placeholder") shouldBe "placeholder"
        result.head("dataFormat") shouldBe "dataFormat"
        result.head("scale") shouldBe 6
        result.head("valueMap") shouldBe Map("0" -> "value")
        result.head("dataType") shouldBe "Hash"
      }
    }

    "return 404 on getting attributes when parent entity is not enabled" in {
      // given
      (tezosPlatformDiscoveryOperations.getEntities _).when().returns(successful(List(Entity("entity", "entity-name", 1))))
      (tezosPlatformDiscoveryOperations.getTableAttributes _).when("entity").returns(successful(Some(List(Attribute("attribute", "attribute-name", Int, None, NonKey, "entity")))))

      val overridesConfiguration = Map("tezos" ->
        PlatformConfiguration(None, Some(true), None, Map("mainnet" ->
          NetworkConfiguration(None, Some(true), None))))

      // when
      Get("/v2/metadata/tezos/mainnet/entity/attributes") ~> addHeader("apiKey", "hooman") ~> sut(overridesConfiguration) ~> check {

        // then
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return 404 on getting attributes when parent network is not enabled" in {
      // given
      (tezosPlatformDiscoveryOperations.getEntities _).when().returns(successful(List(Entity("entity", "entity-name", 1))))
      (tezosPlatformDiscoveryOperations.getTableAttributes _).when("entity").returns(successful(Some(List(Attribute("attribute", "attribute-name", Int, None, NonKey, "entity")))))

      val overridesConfiguration = Map("tezos" ->
        PlatformConfiguration(None, Some(true)))

      // when
      Get("/v2/metadata/tezos/mainnet/entity/attributes") ~> addHeader("apiKey", "hooman") ~> sut(overridesConfiguration) ~> check {

        // then
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return 404 on getting attributes when parent platform is not enabled" in {
      // given
      (tezosPlatformDiscoveryOperations.getEntities _).when().returns(successful(List(Entity("entity", "entity-name", 1))))
      (tezosPlatformDiscoveryOperations.getTableAttributes _).when("entity").returns(successful(Some(List(Attribute("attribute", "attribute-name", Int, None, NonKey, "entity")))))

      // when
      Get("/v2/metadata/tezos/mainnet/entity/attributes") ~> addHeader("apiKey", "hooman") ~> sut(Map.empty) ~> check {

        // then
        status shouldEqual StatusCodes.NotFound
      }
    }
  }
}