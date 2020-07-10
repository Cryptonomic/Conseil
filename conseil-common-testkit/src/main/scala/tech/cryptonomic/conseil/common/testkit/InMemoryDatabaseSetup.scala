package tech.cryptonomic.conseil.common.testkit

import slick.dbio.{DBIOAction, Effect}
import slick.jdbc.PostgresProfile.DDL
import slick.jdbc.PostgresProfile.api._

trait InMemoryDatabaseSetup {

  private var register = Map.empty[String, Seq[Fixture]]

  // Initialization is done only once, before fixtures creation
  def initScripts: Seq[InitScript] =
    register.keys.map { schema =>
      InitScript(s"SET client_encoding = 'UTF8'; CREATE SCHEMA $schema;", "")
    }.toSeq

  // Keep in mind that this is sorted to preserve key consistency
  def fixtures: Seq[Fixture] = register.flatMap(_._2).toSeq

  def registerSchema(schema: String, fixtures: Seq[Fixture]): Unit = register += schema -> fixtures

  /*** Fixture is needed to run away from type madness in Slick */
  trait Fixture {
    def create: DBIOAction[Unit, NoStream, Effect.Schema]
    def delete: DBIOAction[Int, NoStream, Effect.Write]
  }

  /** Companion object for Fixture trait */
  object Fixture {

    /** Creates fixture for tables */
    def table[U](query: TableQuery[_ <: Table[U]]): Fixture = TFixture(query)

    /** Creates fixture for views */
    def view(createSql: String): Fixture = VFixture(createSql)
  }

  /*** Represents fixture for common slick tables */
  case class TFixture[U](query: TableQuery[_ <: Table[U]]) extends Fixture {
    def create: DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.create
    def delete: DBIOAction[Int, NoStream, Effect.Write] = query.delete
  }

  /*** Represents fixture for custom slick views */
  case class VFixture(createSql: String) extends Fixture {
    private val ddl = DDL(createSql, "") // We do not need to drop views

    def create: DBIOAction[Unit, NoStream, Effect.Schema] = ddl.create
    def delete: DBIOAction[Int, NoStream, Effect.Write] = DBIOAction.successful(0)
  }

  /*** Represents the script to initialize the database, before creating tables */
  case class InitScript(createSql: String, dropSql: String) {
    private val ddl = DDL(createSql, dropSql)

    def create: DBIOAction[Unit, NoStream, Effect.Schema] = ddl.create
  }

}
