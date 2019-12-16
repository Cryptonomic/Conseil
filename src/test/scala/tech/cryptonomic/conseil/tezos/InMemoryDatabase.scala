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
  import java.nio.file._
  import slick.jdbc.PostgresProfile.api._

  val dbInstance = new PostgreSQLContainer()
  dbInstance.start()

  /** how to name the database schema for the test */
  protected val databaseName = dbInstance.getDatabaseName

  /** here are temp files for the embedded process, can wipe out if needed */
  protected val cachedRuntimePath = Paths.get("test-postgres-path")

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
    Tables.ProcessedChainEvents,
    Tables.BigMaps,
    Tables.BigMapContents,
    Tables.OriginatedAccountMaps
  )

  protected val dbSchema = Tables.schema
  allTables.map(_.schema).reduce(_ ++ _)

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
    Await.result(dbHandler.run(sql"CREATE SCHEMA IF NOT EXISTS tezos".as[Int]), 1.second)
    Await.result(
      dbHandler.run(sql"""ALTER DATABASE "#$databaseName" SET search_path TO tezos,public""".as[Int]),
      1.second
    )
    Await.result(dbHandler.run(dbSchema.create), 1.second)
  }

  override protected def afterAll(): Unit = {
    Await.ready(dbHandler.run(dbSchema.drop), 1.second)
    dbHandler.close()
    dbInstance.stop()
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    Await.ready(dbHandler.run(truncateAll), 1.second)
    super.beforeEach()
  }

}
