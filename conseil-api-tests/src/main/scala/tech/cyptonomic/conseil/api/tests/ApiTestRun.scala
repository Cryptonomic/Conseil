package tech.cyptonomic.conseil.api.tests

import java.util.concurrent.Executors

import cats.effect.{ExitCode, IO, IOApp, ContextShift}
import io.circe.syntax._
import io.circe.parser._
import org.http4s.Method._
import org.http4s.client.blaze._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.Method.GET
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.headers.{Accept, `Content-Type`}
import org.http4s.{Header, MediaType, Method, Request, Uri}

import scala.concurrent.ExecutionContext

object ApiTestRun extends IOApp {

  val PROTOCOL: String = "https"
  val CONSEIl_HOST: String = "conseil-prod.cryptonomic-infra.tech"
  val CONSEIL_PORT: Int = 443
  val CONSEIl_API_KEY: String = "f86ab59d-d2ea-443b-98e2-6c0785e3de8c"
  val CONSEIL: org.http4s.Uri = Uri.unsafeFromString(PROTOCOL + "://" + CONSEIl_HOST + ":" + CONSEIL_PORT)

  override def run(args: List[String]): IO[ExitCode] = {

    val httpClient: BlazeClientBuilder[IO] = createHttpClient()

    //        TESTING CONSEIL BUILD REQUESTS

    println("\n\n\nTesting Info Endpoint:\n")
    println(sendConseilRequest(
      httpClient,
      CONSEIL.withPath(Requests.ConseilRequests.CONSEIL_BUILD_INFO),
      Requests.TezosValidationLists.TEZOS_INFO_VALIDATION
    ))

    println("\n\n\nTesting Platforms Endpoint\n")
    println(sendConseilRequest(
      httpClient,
      CONSEIL.withPath(Requests.ConseilRequests.CONSEIL_PLATFORMS),
      Requests.TezosValidationLists.TEZOS_PLATFORMS_VALIDATION
    ))

    //        TESTING CONSEIL TEZOS BUILD CONFIG

    println("\n\n\nTesting Tezos Networks\n")
    println(sendConseilRequest(
      httpClient,
      CONSEIL.withPath(Requests.TezosConfig.TEZOS_NETWORKS),
      Requests.TezosValidationLists.TEZOS_NETWORKS_VALIDATION
    ))

    println("\n\n\nTesting Tezos Entitites\n")
    println(sendConseilRequest(
      httpClient,
      CONSEIL.withPath(Requests.TezosConfig.TEZOS_ENTITIES),
      Requests.TezosValidationLists.TEZOS_ENTITIES_VALIDATION
    ))

    //        TESTING TEZOS ENTITY ATTRIBUTES
    testAttributes(httpClient)

    testAttributeData(httpClient)

    validateAttributeData(httpClient)



    //        TESTING TEZOS CHAIN DATA ENDPOINTS

    println("\n\n\nTesting Block Head\n")
    println(sendConseilRequest(httpClient, CONSEIL.withPath(Requests.TezosChainRequests.TEZOS_BLOCK_HEAD)))

    println("\n\n\nTesting Operation Kinds\n")
    println(sendConseilRequest(httpClient, CONSEIL.withPath(Requests.TezosChainRequests.TEZOS_OPERATION_KINDS)))


    IO.pure(ExitCode.Success)
  }

  /**
   * Create the http client used to make requests
   * @return The client object
   */
  def createHttpClient(): BlazeClientBuilder[IO] = {

    val pool = Executors.newCachedThreadPool()
    val clientExecution: ExecutionContext = ExecutionContext.fromExecutor(pool)
    implicit val shift: ContextShift[IO] = IO.contextShift(clientExecution)

    val clientBuild = BlazeClientBuilder[IO](clientExecution, shift)

    clientBuild
  }

  /**
   * Send a conseil request with an API Key and proper headers
   * @param client The BlazeClientBuilder client with which to make the request
   * @param queryUrl The URI to query
   * @return String results from the query performed
   */
  def sendConseilRequest(client: BlazeClientBuilder[IO], queryUrl: Uri, validationItems: List[String] = List[String]()): String = {

    val conseilRequest = GET(
      queryUrl,
      `Content-Type`(MediaType.application.json),
      Accept(MediaType.application.json),
      Header("apiKey", CONSEIl_API_KEY)
    )

    val result: String = client.resource.use { client =>
      client.expect[String](conseilRequest)
    }.unsafeRunSync()

    if(validationItems.nonEmpty) validationItems.foreach(item => {
      validateJsonByKey(item, result, true)
    })

    result
  }

  /**
   * Send a Conseil query
   * @param client The HTTP client used to send the request
   * @param queryUrl The API endpoint to send the query to
   * @param queryBody The body of the query
   * @return The result of the query
   */
  def sendConseilQuery(client: BlazeClientBuilder[IO], queryUrl: Uri, queryBody: String): String = {

    val request = Request[IO](method = Method.POST, uri = queryUrl)
      .withEntity[String](queryBody)
      .withHeaders(
        Header("apiKey", CONSEIl_API_KEY),
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json)
      )

    client.resource.use {client =>
      client.expect[String](request)
    }.unsafeRunSync()
  }

  /**
   * Test retrieval of all tezos attributes from the entities given
   * @param httpClient The BlazeClientBuilder http client to make the conseil request
   * @param entities The Array of entities grab attributes for
   */
  def testAttributes(httpClient: BlazeClientBuilder[IO], entities: Array[String] = Requests.TEZOS_ENTITIES): Unit = {
    entities.foreach(entity => {
      println("\n\n\nTesting Tezos " + entity + " Attributes\n")
      println(sendConseilRequest(httpClient, CONSEIL.withPath(Requests.getTezosAttributePath(entity))))
    })
  }

  def testMetadata(httpClient: BlazeClientBuilder[IO], entityAttributes: Map[String, Array[String]] = Requests.TEZOS_ENTITY_ATTRIBUTES): Unit = {

    entityAttributes.foreach(entity => {

      entity._2.foreach(attribute => {

        println("\n\n\nTesting Tezos " + entity._1 + " " + attribute + " Metadata\n")
        val result: String = sendConseilRequest(httpClient, CONSEIL.withPath(Requests.getTezosMetadataPath(entity._1, attribute)))
        println(result)

      })

    })

  }

  /**
   * Test Conseil Attributes for each entity
   * @param httpClient The BlazeClientBuilder[IO] object to make the requests to conseil
   * @param entityAttributes A Map that has entities as keys, and an array of attributes as its values
   */
  def testAttributeData(httpClient: BlazeClientBuilder[IO], entityAttributes: Map[String, Array[String]] = Requests.TEZOS_ENTITY_ATTRIBUTES): Unit = {

    entityAttributes.foreach(entity => {

      val queryString: String =
        """
          |{
          |     "fields": %FIELDS%,
          |     "predicates": [],
          |     "orderBy": [],
          |     "aggregation": [],
          |     "limit": 10
          |}
          |""".stripMargin.replace("%FIELDS%", entity._2.asJson.toString)

      println("\n\n\nTesting Tezos " + entity._1 + " Query\n")
      val result: String = sendConseilQuery(httpClient, CONSEIL.withPath(Requests.getTezosQueryPath(entity._1)), queryString)
      println(result)

      entity._2.foreach(attribute => {
        validateJsonByKey(attribute, result)
      })

    })

  }

  /**
   * Sends a query to make sure each attribute data is valid
   * @param httpClient The BlazeClientBuilder[IO] object that sends the conseil requests
   * @param entityAttributes A Map that has entities as keys, and an array of attributes as its values
   */
  def validateAttributeData(httpClient: BlazeClientBuilder[IO], entityAttributes: Map[String, Array[String]] = Requests.TEZOS_ENTITY_ATTRIBUTES): Unit = {

    entityAttributes.foreach(entity => {

      entity._2.foreach(attribute => {

        val queryString: String =
          """
            |{
            |     "field": ["%FIELD%"],
            |     "predicates": [
            |         {
            |             "field": "%FIELD%",
            |             "operation": "isnull",
            |             "set": [""],
            |             "inverse": true
            |         }
            |     ],
            |     "orderBy": [],
            |     "aggregation": [],
            |     "limit": 1
            |}
            |""".stripMargin.replace("%FIELD%", attribute)

        println("\n\n\nValidating Tezos " + entity._1 + " " + attribute + " Data\n")
        var result: String = ""
        try {
          result = sendConseilQuery(httpClient, CONSEIL.withPath(Requests.getTezosQueryPath(entity._1)), queryString)
        } catch {
          case x: org.http4s.client.UnexpectedStatus => {

            val queryString: String =
              """
                |{
                |     "field": ["%FIELD%"],
                |     "predicates": [],
                |     "orderBy": [],
                |     "aggregation": [],
                |     "limit": 1
                |}
                |""".stripMargin.replace("%FIELD%", attribute)

            result = sendConseilQuery(httpClient, CONSEIL.withPath(Requests.getTezosQueryPath(entity._1)), queryString)

          }
        }

        println(result)

        validateJsonByKey(attribute, result, notNull = true)

      })
    })
  }

  /**
   * Validate a query output by searching for JSON keys
   * @param key The json key to search for
   * @param result The result of a query to search in
   * @param notNull Whether the key's value should be allowed to be null or not
   */
  def validateJsonByKey(key: String, result: String, notNull: Boolean = false): Unit = {

    if(result.compareTo("") == 0) throw new NullPointerException("Query Returned an Empty String")

    parse(result) match {
      case Left(failure) => throw new IllegalArgumentException("Query did not return valid JSON")
      case Right(json) => {

        if(json.findAllByKey(key).isEmpty) throw new IllegalArgumentException("Value \"" + key + "\" not found in output")
        if(notNull && json.findAllByKey(key).contains(null)) throw new IllegalArgumentException("Value \"" + key + "\" was Null")

        println(key + " was validated!")

      }
    }

  }
}
