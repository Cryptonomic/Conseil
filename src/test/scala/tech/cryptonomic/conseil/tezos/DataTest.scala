package tech.cryptonomic.conseil.tezos

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.provide
import akka.http.scaladsl.server.{Directive1, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import slick.jdbc.PostgresProfile
import tech.cryptonomic.conseil.config.Platforms.{PlatformsConfiguration, Tezos, TezosConfiguration, TezosNodeConfiguration}
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.generic.chain.DataTypes.Query
import tech.cryptonomic.conseil.generic.chain.{ApiNetworkOperations, DataOperations}
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
      |      "set": ["tz1aNTQGugcHFYpC4qdtwEYqzEtw9Uqnd2N1", "KT1HanAHcVwEUD86u9Gz96uCeff9WnF283np"]}
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
      |      "set": ["tz1aNTQGugcHFYpC4qdtwEYqzEtw9Uqnd2N1", "KT1HanAHcVwEUD86u9Gz96uCeff9WnF283np"]}
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

  val responseAsMap: List[Map[String, Any]] = List(
    Map(
      "account_id" -> "tz1aNTQGugcHFYpC4qdtwEYqzEtw9Uqnd2N1",
      "spendable" -> true,
      "counter" -> 1137
    ),
    Map(
      "account_id" -> "KT1HanAHcVwEUD86u9Gz96uCeff9WnF283np",
      "spendable" -> true,
      "counter" -> 2
    )
  )

  val fieldQuery = Query(
    fields = List("account_id", "spendable", "counter"),
    predicates = List.empty
  )

  val fakeQPO: DataOperations = new DataOperations {
    override def queryWithPredicates(tableName: String, query: Query)(implicit ec: ExecutionContext): Future[List[Map[String, Any]]] =
      Future.successful(responseAsMap)
  }

  val cfg = PlatformsConfiguration(
    platforms = Map(
      Tezos -> List(TezosConfiguration("alphanet", TezosNodeConfiguration(protocol = "http", hostname = "localhost", port = 8732)))
    )
  )

//  val apiNetworkOperations: ApiNetworkOperations = new ApiNetworkOperations(cfg, ec) {
//    override def getApiFiltering(platform: String, network: String): Directive1[DatabaseApiFiltering] = {
//      provide{
//        new DatabaseApiFiltering {
//          override def asyncApiFiltersExecutionContext: ExecutionContext = ???
//          override def dbHandle: PostgresProfile.api.Database = ???
//        }
//      }
//    }
//
//    override def getApiOperations(platform: String, network: String): Directive1[ApiOperations] = {
//      provide {
//        new ApiOperations {
//
//        }
//      }
//    }
//  }
//
//  val postRoute: Route = new Data(cfg, apiNetworkOperations)(ec).postRoute
//
//  val getRoute: Route = new Data(cfg, apiNetworkOperations)(ec).getRoute
//
//  "Query protocol" should {
//
//    "return a correct response with OK status code with POST" in {
//
//      val postRequest = HttpRequest(
//        HttpMethods.POST,
//        uri = "/tezos/alphanet/accounts",
//        entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest))
//
//      postRequest ~> postRoute ~> check {
//        val resp = entityAs[String]
//        resp.filterNot(_.isWhitespace) shouldBe jsonStringResponse.filterNot(_.isWhitespace)
//        status shouldBe StatusCodes.OK
//      }
//    }
//
//    "return 400 BadRequest status code for request with missing fields with POST" in {
//      val postRequest = HttpRequest(
//        HttpMethods.POST,
//        uri = "/tezos/alphanet/accounts",
//        entity = HttpEntity(MediaTypes.`application/json`, malformedJsonStringRequest))
//      postRequest ~> postRoute ~> check {
//        status shouldBe StatusCodes.BadRequest
//      }
//    }
//
//    "return 404 NotFound status code for request for the not supported platform with POST" in {
//      val postRequest = HttpRequest(
//        HttpMethods.POST,
//        uri = "/notSupportedPlatform/alphanet/accounts",
//        entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest))
//      postRequest ~> postRoute ~> check {
//        status shouldBe StatusCodes.NotFound
//      }
//    }
//
//    "return a correct response with OK status code with GET" in {
//      val getRequest = HttpRequest(
//        HttpMethods.GET,
//        uri = "/tezos/alphanet/accounts"
//      )
//
//      getRequest ~> getRoute ~> check {
//        val resp = entityAs[String]
//        resp.filterNot(_.isWhitespace) shouldBe jsonStringResponse.filterNot(_.isWhitespace)
//        status shouldBe StatusCodes.OK
//      }
//    }
//
//    "return 404 NotFound status code for request for the not supported platform with GET" in {
//      val getRequest = HttpRequest(
//        HttpMethods.GET,
//        uri = "/notSupportedPlatform/alphanet/accounts"
//      )
//      getRequest ~> getRoute ~> check {
//        status shouldBe StatusCodes.NotFound
//      }
//    }
//  }
}
