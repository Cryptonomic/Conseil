package tech.cryptonomic.conseil.common.testkit

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestSuite}
import org.testcontainers.containers.PostgreSQLContainer

import scala.concurrent.Await
import scala.concurrent.duration._

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
    Await.ready(dbHandler.run(initScript.create), 1.second)
    Await.ready(dbHandler.run(DBIO.sequence(fixtures.map(_.create))), 1.second)
    ()
  }

  override protected def afterAll(): Unit = {
    Await.ready(dbHandler.run(DBIO.sequence(fixtures.reverse.map(_.delete))), 1.second)
    dbHandler.close()
    dbInstance.stop()
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Await.ready(dbHandler.run(DBIO.sequence(fixtures.reverse.map(_.delete))), 1.second)
    ()
  }

}
