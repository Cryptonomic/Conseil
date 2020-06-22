package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import tech.cryptonomic.conseil.api.metadata.{
  AttributeValuesCacheConfiguration,
  MetadataService,
  TransparentUnitTransformation
}
import tech.cryptonomic.conseil.api.routes.platform.discovery.TestPlatformDiscoveryOperations
import tech.cryptonomic.conseil.common.config.Platforms.{BitcoinConfiguration, PlatformsConfiguration}
import tech.cryptonomic.conseil.common.config.{MetadataConfiguration, Platforms}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse}
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Attribute, DataType, Entity, KeyType}
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}

import scala.concurrent.{ExecutionContext, Future}

class BitcoinDataRoutesTest
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with MockFactory
    with BeforeAndAfterEach
    with BitcoinDataRoutesTest.Fixtures {

  private val testEntity = Entity("blocks", "Test Entity", 0)
  private val testNetworkPath = NetworkPath("mainnet", PlatformPath("bitcoin"))
  private val testEntityPath = EntityPath("blocks", testNetworkPath)
  private val platformDiscoveryOperations = new TestPlatformDiscoveryOperations
  platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
  blockAttributes.foreach(platformDiscoveryOperations.addAttribute(testEntityPath, _))

  private val conseilOps: BitcoinDataOperations = new BitcoinDataOperations {
    override def queryWithPredicates(prefix: String, tableName: String, query: Query)(
        implicit ec: ExecutionContext
    ): Future[List[QueryResponse]] =
      Future.successful(responseAsMap)
  }

  private val metadataService =
    new MetadataService(
      PlatformsConfiguration(Map(Platforms.Bitcoin -> List(BitcoinConfiguration("Mainnet")))),
      TransparentUnitTransformation,
      stub[AttributeValuesCacheConfiguration],
      platformDiscoveryOperations
    )
  private val routes: BitcoinDataRoutes =
    BitcoinDataRoutes(metadataService, MetadataConfiguration(Map.empty), conseilOps, 1000)

  "Query protocol" should {
      "return a correct response with OK status code with GET" in {
        val getRequest = HttpRequest(HttpMethods.GET, uri = "/v2/data/bitcoin/mainnet/blocks")

        getRequest ~> addHeader("apiKey", "hooman") ~> routes.getRoute ~> check {
          val resp = entityAs[String]
          resp.filterNot(_.isWhitespace) shouldBe jsonStringResponse.filterNot(_.isWhitespace)
          status shouldBe StatusCodes.OK
        }
      }
    }
}

object BitcoinDataRoutesTest {
  trait Fixtures {
    val jsonStringRequest: String =
      """
        |{
        |  "fields": ["account_id", "spendable", "counter"],
        |  "predicates": [
        |    {
        |      "field": "account_id",
        |      "operation": "in",
        |      "set": ["tz1aNTQGugcHFYpC4qdtwEYqzEtw9Uqnd2N1", "KT1HanAHcVwEUD86u9Gz96uCeff9WnF283np"],
        |      "inverse": false
        |    }
        |  ]
        |}
        |
    """.stripMargin

    val jsonStringResponse: String =
      """
        |[{
        |  "account_id" : "tz1aNTQGugcHFYpC4qdtwEYqzEtw9Uqnd2N1",
        |  "spendable" : true,
        |  "counter" : 1137
        |}, {
        |  "account_id" : "KT1HanAHcVwEUD86u9Gz96uCeff9WnF283np",
        |  "spendable" : true,
        |  "counter" : 2
        |}]
    """.stripMargin

    val responseAsMap: List[QueryResponse] = List(
      Map(
        "account_id" -> Some("tz1aNTQGugcHFYpC4qdtwEYqzEtw9Uqnd2N1"),
        "spendable" -> Some(true),
        "counter" -> Some(1137)
      ),
      Map(
        "account_id" -> Some("KT1HanAHcVwEUD86u9Gz96uCeff9WnF283np"),
        "spendable" -> Some(true),
        "counter" -> Some(2)
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
      )
    )
  }
}
