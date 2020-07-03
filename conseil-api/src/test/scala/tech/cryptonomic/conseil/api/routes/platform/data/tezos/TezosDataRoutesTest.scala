package tech.cryptonomic.conseil.api.routes.platform.data.tezos

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
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.config.Platforms._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, QueryResponse, SimpleField}
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Attribute, DataType, Entity, KeyType}
import tech.cryptonomic.conseil.common.metadata.{EntityPath, NetworkPath, PlatformPath}

import scala.concurrent.{ExecutionContext, Future}

class TezosDataRoutesTest
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with ScalaFutures
    with MockFactory
    with BeforeAndAfterEach
    with TezosDataRoutesTest.Fixtures {

  private val conseilOps: TezosDataOperations = new TezosDataOperations {
    override def queryWithPredicates(prefix: String, tableName: String, query: Query)(
        implicit ec: ExecutionContext
    ): Future[List[QueryResponse]] =
      Future.successful(responseAsMap)
  }

  val cfg = PlatformsConfiguration(
    List(
      TezosConfiguration(
        "alphanet",
        enabled = true,
        TezosNodeConfiguration(protocol = "http", hostname = "localhost", port = 8732),
        None
      )
    )
  )

  val testEntity = Entity("accounts", "Test Entity", 0)
  val testNetworkPath = NetworkPath("alphanet", PlatformPath("tezos"))
  val testEntityPath = EntityPath("accounts", testNetworkPath)

  val platformDiscoveryOperations = new TestPlatformDiscoveryOperations
  platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
  accountAttributes.foreach(platformDiscoveryOperations.addAttribute(testEntityPath, _))

  val cacheOverrides = stub[AttributeValuesCacheConfiguration]

  val metadataConf = MetadataConfiguration(Map.empty)

  val metadataService =
    new MetadataService(
      PlatformsConfiguration(
        List(
          TezosConfiguration("alphanet", enabled = true, TezosNodeConfiguration("tezos-host", 123, "https://"), None)
        )
      ),
      TransparentUnitTransformation,
      cacheOverrides,
      platformDiscoveryOperations
    )

  private val routes: TezosDataRoutes = TezosDataRoutes(metadataService, metadataConf, conseilOps, 1000)

  "Query protocol" should {

      "return a correct response with OK status code with POST" in {

        val postRequest = HttpRequest(
          HttpMethods.POST,
          uri = "/v2/data/tezos/alphanet/accounts",
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
          uri = "/v2/data/notSupportedPlatform/alphanet/accounts",
          entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest)
        )
        postRequest ~> addHeader("apiKey", "hooman") ~> routes.postRoute ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }

      "return 404 NotFound status code for request for the not supported network with POST" in {

        val postRequest = HttpRequest(
          HttpMethods.POST,
          uri = "/v2/data/tezos/notSupportedNetwork/accounts",
          entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest)
        )
        postRequest ~> addHeader("apiKey", "hooman") ~> routes.postRoute ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }

      "return a correct response with OK status code with GET" in {
        val getRequest = HttpRequest(
          HttpMethods.GET,
          uri = "/v2/data/tezos/alphanet/accounts"
        )

        getRequest ~> addHeader("apiKey", "hooman") ~> routes.getRoute ~> check {
          val resp = entityAs[String]
          resp.filterNot(_.isWhitespace) shouldBe jsonStringResponse.filterNot(_.isWhitespace)
          status shouldBe StatusCodes.OK
        }
      }

      "not handle request for the not supported platform with GET" in {
        // Due to the fact that platforms are hardcoded in path (not dynamic),
        // request won't be handled for unsupported platforms and pushed down to the default rejection handler.
        val getRequest = HttpRequest(
          HttpMethods.GET,
          uri = "/v2/data/notSupportedPlatform/alphanet/accounts"
        )
        getRequest ~> addHeader("apiKey", "hooman") ~> routes.getRoute ~> check {
          handled shouldBe false
        }
      }

      "return 404 NotFound status code for request for the not supported network with GET" in {
        val getRequest = HttpRequest(
          HttpMethods.GET,
          uri = "/v2/data/tezos/notSupportedNetwork/accounts"
        )
        getRequest ~> addHeader("apiKey", "hooman") ~> routes.getRoute ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }
}

object TezosDataRoutesTest {
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

    val malformedJsonStringRequest: String =
      """
        |{
        |  "fields": ["account_id", "spendable", "counter"],
        |  "predicates": [
        |    {
        |      "operation": "in",
        |      "set": ["tz1aNTQGugcHFYpC4qdtwEYqzEtw9Uqnd2N1", "KT1HanAHcVwEUD86u9Gz96uCeff9WnF283np"]
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

    val fieldQuery = Query(
      fields = List(SimpleField("account_id"), SimpleField("spendable"), SimpleField("counter")),
      predicates = List.empty
    )

    val accountAttributes = List(
      Attribute(
        name = "account_id",
        displayName = "Account Id",
        dataType = DataType.String,
        cardinality = None,
        keyType = KeyType.UniqueKey,
        entity = "accounts"
      ),
      Attribute(
        name = "spendable",
        displayName = "Spendable",
        dataType = DataType.Boolean,
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "accounts"
      ),
      Attribute(
        name = "counter",
        displayName = "Counter",
        dataType = DataType.Int,
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "accounts"
      )
    )
  }
}
