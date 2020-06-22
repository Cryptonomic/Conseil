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
    with BeforeAndAfterEach {

  val blockAttributes = List( //TODO Change below fields
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

  private val conseilOps: BitcoinDataOperations = new BitcoinDataOperations {
    override def queryWithPredicates(prefix: String, tableName: String, query: Query)(
        implicit ec: ExecutionContext
    ): Future[List[QueryResponse]] =
      Future.successful(List(Map.empty))
  }
  private val testEntity = Entity("blocks", "Test Entity", 0)
  private val testNetworkPath = NetworkPath("mainnet", PlatformPath("Bitcoin"))
  private val testEntityPath = EntityPath("blocks", testNetworkPath)
  private val platformDiscoveryOperations = new TestPlatformDiscoveryOperations
  platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
  blockAttributes.foreach(platformDiscoveryOperations.addAttribute(testEntityPath, _))

  private val metadataConf = MetadataConfiguration(Map.empty)
  private val metadataService =
    new MetadataService(
      PlatformsConfiguration(Map(Platforms.Bitcoin -> List(BitcoinConfiguration("Mainnet")))),
      TransparentUnitTransformation,
      stub[AttributeValuesCacheConfiguration],
      platformDiscoveryOperations
    )
  private val routes: BitcoinDataRoutes = BitcoinDataRoutes(metadataService, metadataConf, conseilOps, 1000)

  "" should {
      "return a correct response with OK status code with GET" in {
        val getRequest = HttpRequest(
          HttpMethods.GET,
          uri = "/v2/data/tezos/alphanet/accounts"
        )

        getRequest ~> addHeader("apiKey", "hooman") ~> routes.getRoute ~> check {
          val resp = entityAs[String]
//          resp.filterNot(_.isWhitespace) shouldBe jsonStringResponse.filterNot(_.isWhitespace)
          status shouldBe StatusCodes.OK
        }
      }
    }

}
