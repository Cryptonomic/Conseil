package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestSuite}
import org.testcontainers.containers.PostgreSQLContainer

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Provides access to a test in-memory database initialized with conseil schema
  */
trait InMemoryDatabase extends BeforeAndAfterAll with BeforeAndAfterEach {
  self: TestSuite =>
  import slick.jdbc.PostgresProfile.api._

  //#JavaThankYou
  val dbInstance =
    new PostgreSQLContainer("postgres:11.6")
      .asInstanceOf[PostgreSQLContainer[_]]
      .withInitScript("in-memory-db/init-script.sql") //startup will prepare the schema
      .asInstanceOf[PostgreSQLContainer[_]]
      .withCommand("-c full_page_writes=off") //should improve performance for the tests
      .asInstanceOf[PostgreSQLContainer[_]]

  dbInstance.start()

  /** how to name the database schema for the test */
  protected val databaseName = dbInstance.getDatabaseName

  /** defines configuration for a randomly named embedded instance */
  protected lazy val confString =
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

  //keep in mind that this is sorted to preserve key consistency
  protected val allTables = Seq(
    Tables.Blocks,
    Tables.OperationGroups,
    Tables.Operations,
    Tables.BalanceUpdates,
    Tables.Accounts,
    Tables.Delegates,
    Tables.Fees,
    Tables.AccountsCheckpoint,
    Tables.DelegatesCheckpoint,
    Tables.Rolls,
    Tables.AccountsHistory,
    Tables.ProcessedChainEvents
  )

  protected val dbSchema = Tables.schema

  /**
    * calling deletes manually is needed to obviate the fact
    * that TRUNCATE TABLE won't work correctly
    * when there are table constraints
    */
  protected val truncateAll = DBIO.sequence(
    allTables.reverse.map(_.delete)
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    dbInstance.start()
    Await.result(dbHandler.run(dbSchema.create), 1.second)
  }

  override protected def afterAll(): Unit = {
    Await.ready(dbHandler.run(dbSchema.drop), 1.second)
    dbHandler.close()
    dbInstance.stop()
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Await.ready(dbHandler.run(truncateAll), 1.second)
    ()
  }

}
