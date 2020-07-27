package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import tech.cryptonomic.conseil.api.metadata.{
  AttributeValuesCacheConfiguration,
  MetadataService,
  TransparentUnitTransformation
}
import tech.cryptonomic.conseil.api.routes.platform.discovery.TestPlatformDiscoveryOperations
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.config.Platforms.{EthereumConfiguration, PlatformsConfiguration}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Attribute, DataType, Entity, KeyType}
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}

import scala.concurrent.{ExecutionContext, Future}

class EthereumDataRoutesTest
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with MockFactory
    with BeforeAndAfterEach
    with EthereumDataRoutesTest.Fixtures {

  private val testEntity = Entity("blocks", "Test Entity", 0)
  private val testNetworkPath = NetworkPath("mainnet", PlatformPath("ethereum"))
  private val testEntityPath = EntityPath("blocks", testNetworkPath)
  private val platformDiscoveryOperations = new TestPlatformDiscoveryOperations
  platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
  blockAttributes.foreach(platformDiscoveryOperations.addAttribute(testEntityPath, _))

  private val conseilOps: EthereumDataOperations = new EthereumDataOperations("ethereum") {
    override def queryWithPredicates(prefix: String, tableName: String, query: Query)(
        implicit ec: ExecutionContext
    ): Future[List[QueryResponse]] =
      Future.successful(responseAsMap)
  }

  private val metadataService =
    new MetadataService(
      PlatformsConfiguration(
        List(
          EthereumConfiguration(
            "mainnet",
            enabled = true
          )
        )
      ),
      TransparentUnitTransformation,
      stub[AttributeValuesCacheConfiguration],
      platformDiscoveryOperations
    )
  private val routes: EthereumDataRoutes =
    EthereumDataRoutes(metadataService, MetadataConfiguration(Map.empty), conseilOps, 1000)

  "Query protocol" should {
    "return a correct response with OK status code with POST" in {

      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/v2/data/ethereum/mainnet/blocks",
        entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest)
      )

      postRequest ~> addHeader("apiKey", "hooman") ~> routes.postRoute ~> check {
        val resp = entityAs[String]
        resp.filterNot(_.isWhitespace) shouldBe jsonStringResponse.filterNot(_.isWhitespace)
        status shouldBe StatusCodes.OK
      }
    }

    "return 404 NotFound status code for request for the not supported platform with POST" in {

      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/v2/data/notSupportedPlatform/mainnet/blocks",
        entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest)
      )
      postRequest ~> addHeader("apiKey", "hooman") ~> routes.postRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return 404 NotFound status code for request for the not supported network with POST" in {

      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/v2/data/ethereum/notSupportedNetwork/blocks",
        entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest)
      )
      postRequest ~> addHeader("apiKey", "hooman") ~> routes.postRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a correct response with OK status code with GET" in {
      val getRequest = HttpRequest(HttpMethods.GET, uri = "/v2/data/ethereum/mainnet/blocks")

      getRequest ~> addHeader("apiKey", "hooman") ~> routes.getRoute ~> check {
        val resp = entityAs[String]
        resp.filterNot(_.isWhitespace) shouldBe jsonStringResponse.filterNot(_.isWhitespace)
        status shouldBe StatusCodes.OK
      }
    }
  }

  "not handle request for the not supported platform with GET" in {
    // Due to the fact that platforms are hardcoded in path (not dynamic),
    // request won't be handled for unsupported platforms and pushed down to the default rejection handler.
    val getRequest = HttpRequest(
      HttpMethods.GET,
      uri = "/v2/data/notSupportedPlatform/mainnet/blocks"
    )
    getRequest ~> addHeader("apiKey", "hooman") ~> routes.getRoute ~> check {
      handled shouldBe false
    }
  }

  "return 404 NotFound status code for request for the not supported network with GET" in {
    val getRequest = HttpRequest(
      HttpMethods.GET,
      uri = "/v2/data/ethereum/notSupportedNetwork/blocks"
    )
    getRequest ~> addHeader("apiKey", "hooman") ~> routes.getRoute ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

}

object EthereumDataRoutesTest {
  trait Fixtures {
    val jsonStringRequest: String =
      """
        |{
        |  "fields": ["hash", "size", "version", "network"],
        |  "predicates": [
        |    {
        |      "field": "hash",
        |      "operation": "in",
        |      "set": ["f88ad67178fadfc38d57f7e662effc1ddea54f13120dbeefd894cb90ac3c5895"],
        |      "inverse": false
        |    }
        |  ]
        |}
        |
    """.stripMargin

    val jsonStringResponse: String =
      """
        |[{
        |  "hash" : "f88ad67178fadfc38d57f7e662effc1ddea54f13120dbeefd894cb90ac3c5895",
        |  "size" : 130481,
        |  "version" : 1,
        |  "network": "mainnet"
        |}]
    """.stripMargin

    val responseAsMap: List[QueryResponse] = List(
      Map(
        "hash" -> Some("f88ad67178fadfc38d57f7e662effc1ddea54f13120dbeefd894cb90ac3c5895"),
        "size" -> Some(130481),
        "version" -> Some(1),
        "network" -> Some("mainnet")
      )
    )

    val blockAttributes = List(
      Attribute(
        name = "hash",
        displayName = "Hash",
        dataType = DataType.String,
        cardinality = None,
        keyType = KeyType.UniqueKey,
        entity = "blocks"
      ),
      Attribute(
        name = "size",
        displayName = "Size",
        dataType = DataType.Int,
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "blocks"
      ),
      Attribute(
        name = "version",
        displayName = "Version",
        dataType = DataType.Int,
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "blocks"
      ),
      Attribute(
        name = "network",
        displayName = "Network",
        dataType = DataType.String,
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "blocks"
      )
    )
  }
}
