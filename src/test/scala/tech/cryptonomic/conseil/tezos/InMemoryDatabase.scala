package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, TestSuite}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Provides access to a test in-memory database initialized with conseil schema
  */
trait InMemoryDatabase extends BeforeAndAfterAll {
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

  protected def createSchemaIO =
    DBIO.seq(
      Tables.Blocks.schema.create,
      Tables.Accounts.schema.create,
      Tables.Fees.schema.create,
      Tables.OperationGroups.schema.create,
      Tables.Operations.schema.create
    ).transactionally


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(dbHandler.run(createSchemaIO), 1 second)
  }

  override def afterAll(): Unit = {
    dbHandler.close()
    super.afterAll()
  }
}
