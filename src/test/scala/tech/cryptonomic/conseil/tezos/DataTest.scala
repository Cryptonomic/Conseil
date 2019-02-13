package tech.cryptonomic.conseil.tezos

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.config.Newest
import tech.cryptonomic.conseil.config.Platforms.{PlatformsConfiguration, Tezos, TezosConfiguration, TezosNodeConfiguration}
import tech.cryptonomic.conseil.generic.chain.DataTypes.Query
import tech.cryptonomic.conseil.generic.chain.{DataOperations, DataPlatform}
import tech.cryptonomic.conseil.routes.Data

import scala.concurrent.{ExecutionContext, Future}

class DataTest extends WordSpec with Matchers with ScalatestRouteTest with ScalaFutures with MockFactory {
  val ec: ExecutionContext = system.dispatcher

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

  val responseAsMap: List[Map[String, Option[Any]]] = List(
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
    fields = List("account_id", "spendable", "counter"),
    predicates = List.empty
  )

  val fakeQPO: DataOperations = new DataOperations {
    override def queryWithPredicates(tableName: String, query: Query)(implicit ec: ExecutionContext): Future[List[Map[String, Option[Any]]]] =
      Future.successful(responseAsMap)
  }

  val fakeQPP: DataPlatform = new DataPlatform(Map("tezos" -> fakeQPO))
  val cfg = PlatformsConfiguration(
    platforms = Map(
      Tezos -> List(TezosConfiguration("alphanet", Newest, TezosNodeConfiguration(protocol = "http", hostname = "localhost", port = 8732)))
    )
  )
  val postRoute: Route = new Data(cfg, fakeQPP)(ec).postRoute

  val getRoute: Route = new Data(cfg, fakeQPP)(ec).getRoutes

  "Query protocol" should {

    "return a correct response with OK status code with POST" in {

      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/v2/data/tezos/alphanet/accounts",
        entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest)
      )

      postRequest ~> addHeader("apiKey", "hooman") ~> postRoute ~> check {
        val resp = entityAs[String]
        resp.filterNot(_.isWhitespace) shouldBe jsonStringResponse.filterNot(_.isWhitespace)
        status shouldBe StatusCodes.OK
      }
    }

    "return 404 NotFound status code for request for the not supported platform with POST" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/v2/data/notSupportedPlatform/alphanet/accounts",
        entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest))
      postRequest ~> addHeader("apiKey", "hooman") ~> postRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return 404 NotFound status code for request for the not supported network with POST" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/v2/data/tezos/notSupportedNetwork/accounts",
        entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest))
      postRequest ~> addHeader("apiKey", "hooman") ~> postRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a correct response with OK status code with GET" in {
      val getRequest = HttpRequest(
        HttpMethods.GET,
        uri = "/v2/data/tezos/alphanet/accounts"
      )

      getRequest ~> addHeader("apiKey", "hooman") ~> getRoute ~> check {
        val resp = entityAs[String]
        resp.filterNot(_.isWhitespace) shouldBe jsonStringResponse.filterNot(_.isWhitespace)
        status shouldBe StatusCodes.OK
      }
    }

    "return 404 NotFound status code for request for the not supported platform with GET" in {
      val getRequest = HttpRequest(
        HttpMethods.GET,
        uri = "/v2/data/notSupportedPlatform/alphanet/accounts"
      )
      getRequest ~> addHeader("apiKey", "hooman") ~> getRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return 404 NotFound status code for request for the not supported network with GET" in {
      val getRequest = HttpRequest(
        HttpMethods.GET,
        uri = "/v2/data/tezos/notSupportedNetwork/accounts"
      )
      getRequest ~> addHeader("apiKey", "hooman") ~> getRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }
}
