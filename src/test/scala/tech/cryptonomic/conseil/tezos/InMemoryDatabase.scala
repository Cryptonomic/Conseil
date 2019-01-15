package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestSuite}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Provides access to a test in-memory database initialized with conseil schema
  */
trait InMemoryDatabase extends BeforeAndAfterAll with BeforeAndAfterEach {
  self: TestSuite =>
  import java.nio.file._
  import scala.collection.JavaConverters._
  import slick.jdbc.PostgresProfile.api._
  import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres
  import ru.yandex.qatools.embed.postgresql.distribution.Version

  /** how to name the database schema for the test */
  protected val databaseName = "conseil-test"
  /** port to use, try to avoid conflicting usage */
  protected val databasePort = 5433
  /** here are temp files for the embedded process, can wipe out if needed */
  protected val cachedRuntimePath = Paths.get("test-postgres-path")
  /** defines configuration for a randomly named embedded instance */
  protected val confString =
    s"""conseildb = {
       |    url                 = "jdbc:postgresql://localhost:$databasePort/$databaseName"
       |    connectionPool      = disabled
       |    keepAliveConnection = true
       |    driver              = org.postgresql.Driver
       |    properties = {
       |      user     = ${EmbeddedPostgres.DEFAULT_USER}
       |      password = ${EmbeddedPostgres.DEFAULT_PASSWORD}
       |    }
       |  }
    """.stripMargin

  /* turns off anti-corruption guarantees settings that will improve performance on testing
   * override to change or add test-specific settings
   */
  protected val pgInitParams = List("--nosync", "--lc-collate=C")
  /* turns off anti-corruption guarantees settings that will improve performance on testing
   * override to change or add test-specific settings
   */
  protected val pgConfigs = List("-c", "full_page_writes=off")

  lazy val dbInstance = new EmbeddedPostgres(Version.V9_5_15)
  lazy val dbHandler: Database = Database.forConfig("conseildb", config = ConfigFactory.parseString(confString))

  //keep in mind that this is sorted to preserve key consistency
  protected val allTables= Seq(
    Tables.Blocks,
    Tables.OperationGroups,
    Tables.Operations,
    Tables.Accounts,
    Tables.Fees,
    Tables.AccountsCheckpoint
  )

  protected val dbSchema =
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
    dbInstance.start(
      EmbeddedPostgres.cachedRuntimeConfig(cachedRuntimePath),
      "localhost",
      databasePort,
      databaseName,
      EmbeddedPostgres.DEFAULT_USER,
      EmbeddedPostgres.DEFAULT_PASSWORD,
      pgInitParams.asJava,
      pgConfigs.asJava)
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
