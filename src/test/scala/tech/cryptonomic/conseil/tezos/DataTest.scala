package tech.cryptonomic.conseil.tezos

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.generic.chain.{DataOperations, DataPlatform}
import tech.cryptonomic.conseil.routes.Data
import tech.cryptonomic.conseil.generic.chain.DataTypes.Query

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

  val malforemdJsonStringRequest: String =
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

  val fakeQPP: DataPlatform = new DataPlatform(Map("tezos" -> fakeQPO))
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
  val route: Route = new Data(cfg, fakeQPP)(ec).route

  "Query protocol" should {

    "return a correct response with OK status code" in {

      val getRequest = HttpRequest(
        HttpMethods.GET,
        uri = "/tezos/alphanet/accounts",
        entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest))

      getRequest ~> route ~> check {
        val resp = entityAs[String]
        resp.filterNot(_.isWhitespace) shouldBe jsonStringResponse.filterNot(_.isWhitespace)
        status shouldBe StatusCodes.OK
      }
    }

    "return 400 BadRequest status code for request with missing fields" in {
      val getRequest = HttpRequest(
        HttpMethods.GET,
        uri = "/tezos/alphanet/accounts",
        entity = HttpEntity(MediaTypes.`application/json`, malforemdJsonStringRequest))
      getRequest ~> route ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "return 404 NotFound status code for request for the not supported platform" in {
      val getRequest = HttpRequest(
        HttpMethods.GET,
        uri = "/notSupportedPlatform/alphanet/accounts",
        entity = HttpEntity(MediaTypes.`application/json`, jsonStringRequest))
      getRequest ~> route ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }
}
