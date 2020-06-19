package tech.cryptonomic.conseil.common.testkit

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestSuite}
import org.testcontainers.containers.PostgreSQLContainer

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Provides access to a test in-memory database initialized with conseil schema
  */
trait InMemoryDatabase extends BeforeAndAfterAll with BeforeAndAfterEach with InMemoryDatabaseSetup {
  self: TestSuite =>
  import slick.jdbc.PostgresProfile.api._

  //#JavaThankYou
  val dbInstance: PostgreSQLContainer[_] =
    new PostgreSQLContainer("postgres:11.6")
      .asInstanceOf[PostgreSQLContainer[_]]
      .withCommand("-c full_page_writes=off") //should improve performance for the tests
      .asInstanceOf[PostgreSQLContainer[_]]

  dbInstance.start()

  /** how to name the database schema for the test */
  protected val databaseName: String = dbInstance.getDatabaseName

  /** defines configuration for a randomly named embedded instance */
  protected lazy val confString: String =
    s"""testdb = {
       |    url                 = "${dbInstance.getJdbcUrl}"
       |    connectionPool      = disabled
       |    keepAliveConnection = true
       |    driver              = org.postgresql.Driver
       |    properties = {
       |      user     = ${dbInstance.getUsername}
       |      password = ${dbInstance.getPassword}
       |    }
       |  }
    """.stripMargin

  lazy val dbHandler: Database = Database.forConfig("testdb", config = ConfigFactory.parseString(confString))

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    dbInstance.start()
    await(dbHandler.run(DBIO.sequence(initScripts.map(_.create))), 2.second)
    await(dbHandler.run(DBIO.sequence(fixtures.map(_.create))), 2.second)
    ()
  }

  override protected def afterAll(): Unit = {
    await(dbHandler.run(DBIO.sequence(fixtures.reverse.map(_.delete))), 2.second)
    dbHandler.close()
    dbInstance.stop()
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(dbHandler.run(DBIO.sequence(fixtures.reverse.map(_.delete))), 2.second)
    ()
  }

  /** Awaits until specific operation has been completed and prints stacktrace on failure to avoid 'silence' bugs */
  private def await[A](f: Future[A], atMost: Duration): Unit =
    Await.ready(f, atMost).failed.foreach(_.printStackTrace())(ExecutionContext.global)

}
