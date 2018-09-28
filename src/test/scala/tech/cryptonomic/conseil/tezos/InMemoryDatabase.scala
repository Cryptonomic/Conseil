package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestSuite}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Provides access to a test in-memory database initialized with conseil schema
  */
trait InMemoryDatabase extends BeforeAndAfterAll with BeforeAndAfterEach {
  self: TestSuite =>

  /** defines configuration for a randomly named h2 in-memory instance */
  protected val confString =
    s"""conseildb = {
       |    url = "jdbc:h2:mem:conseil-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
       |    driver              = org.h2.Driver
       |    connectionPool      = disabled
       |    keepAliveConnection = true
       |  }
    """.stripMargin

  val dbHandler: Database = Database.forConfig("conseildb", config = ConfigFactory.parseString(confString))

  protected lazy val createDatabaseSchema =
    DBIO.sequence(
      allTables.map(_.schema.create)
    ).transactionally

  protected lazy val cleanAllData =
    DBIO.sequence(
      allTables.map(_.delete).reverse //watch out for foreign key integrity
    ).transactionally

  //keep in mind that this is sorted to preserve key consistency
  protected val allTables = Seq(
    Tables.Blocks,
    Tables.Accounts,
    Tables.Fees,
    Tables.OperationGroups,
    Tables.Operations
  )

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(dbHandler.run(createDatabaseSchema), 1 second)
  }

  override protected def afterAll(): Unit = {
    dbHandler.close()
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    Await.ready(dbHandler.run(cleanAllData), 10.millis)
    super.beforeEach()
  }

}
