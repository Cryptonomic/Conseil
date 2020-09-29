package tech.cryptonomic.conseil.common.testkit

import slick.dbio.{DBIOAction, Effect}
import slick.jdbc.PostgresProfile.DDL
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

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
    def create: DBIOAction[Unit, NoStream, Effect.Schema] = DBIO.successful(())
    def delete: DBIOAction[Int, NoStream, Effect.Write] = DBIO.successful(0)
  }

  /** Companion object for Fixture trait */
  object Fixture {

    /** Creates fixture for tables */
    def table[U](query: TableQuery[_ <: Table[U]]): Fixture = TFixture(query)

    /** Creates fixture for views */
    def view(createSql: String): Fixture = view(Iterable(createSql))
    def view(createAction: SqlAction[Int, NoStream, Effect]): Fixture = view(createAction.statements)
    def view(createStatements: Iterable[String]): Fixture = VFixture(createStatements)
  }

  /*** Represents fixture for common slick tables */
  case class TFixture[U](query: TableQuery[_ <: Table[U]]) extends Fixture {
    override def create: DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.create
    override def delete: DBIOAction[Int, NoStream, Effect.Write] = query.delete
  }

  /*** Represents fixture for custom slick views */
  case class VFixture(createSql: Iterable[String]) extends Fixture {
    private val ddl = DDL(createSql, Iterable.empty) // We do not need to drop views

    override def create: DBIOAction[Unit, NoStream, Effect.Schema] = ddl.create
  }

  /*** Represents the script to initialize the database, before creating tables */
  case class InitScript(createSql: String, dropSql: String) {
    private val ddl = DDL(createSql, dropSql)

    def create: DBIOAction[Unit, NoStream, Effect.Schema] = ddl.create

    def customize: DBIOAction[Unit, NoStream, Effect.All] = DBIO.successful(())
  }

}
