package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import com.stephenn.scalatest.jsonassert.JsonMatchers
import com.typesafe.config.ConfigFactory
import tech.cryptonomic.conseil.api.metadata.{
  AttributeValuesCacheConfiguration,
  MetadataService,
  TransparentUnitTransformation
}
import tech.cryptonomic.conseil.api.routes.platform.discovery.TestPlatformDiscoveryOperations
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.config.Platforms.{
  EthereumBatchFetchConfiguration,
  EthereumConfiguration,
  PlatformsConfiguration
}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Attribute, DataType, Entity, KeyType}
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.net.URL
import tech.cryptonomic.conseil.common.config.Platforms.EthereumRetryConfiguration
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class EthereumDataRoutesTest
    extends ConseilSpec
    with ScalatestRouteTest
    with JsonMatchers
    with MockFactory
    with BeforeAndAfterEach
    with EthereumDataRoutesTest.Fixtures {

  private val testEntity = Entity("blocks", "Test Entity", 0)
  private val testNetworkPath = NetworkPath("mainnet", PlatformPath("ethereum"))
  private val testEntityPath = EntityPath("blocks", testNetworkPath)
  private val platformDiscoveryOperations = new TestPlatformDiscoveryOperations
  platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
  blockAttributes.foreach(platformDiscoveryOperations.addAttribute(testEntityPath, _))

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

  private val conseilOps: EthereumDataOperations = new EthereumDataOperations("ethereum", dbCfg) {
    override def queryWithPredicates(prefix: String, tableName: String, query: Query, hideForkInvalid: Boolean = false)(
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
            enabled = true,
            new URL("http://localhost"),
            dbCfg,
            EthereumRetryConfiguration(1.second, 1),
            EthereumBatchFetchConfiguration(1, 1, 1, 1, 1, 1)
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
          resp should matchJson(jsonStringResponse)
          status shouldBe StatusCodes.OK
        }
      }

      "return 404 NotFound status code for request for the unsupported platform with POST" in {

        val postRequest = HttpRequest(
          HttpMethods.POST,
          uri = "/v2/data/notSupportedPlatform/mainnet/blocks",
          entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest)
        )
        postRequest ~> addHeader("apiKey", "hooman") ~> Route.seal(routes.postRoute) ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }

      "return 404 NotFound status code for request for the unsupported network with POST" in {

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
          resp should matchJson(jsonStringResponse)
          status shouldBe StatusCodes.OK
        }
      }
    }

  "not handle request for the unsupported platform with GET" in {
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

  "return 404 NotFound status code for request for the unsupported network with GET" in {
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
