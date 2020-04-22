package tech.cryptonomic.conseil.common.testkit

import slick.jdbc.PostgresProfile.DDL
import slick.jdbc.PostgresProfile.api._

trait InMemoryDatabaseSetup {

  def schema: String

  // Initialization is done only once, before fixtures creation
  def initScript: InitScript = InitScript(s"SET client_encoding = 'UTF8'; CREATE SCHEMA $schema;", "")

  // Keep in mind that this is sorted to preserve key consistency
  def fixtures: Seq[Fixture[_]]

  /*** Table fixture is needed to run away from type madness in Slick */
  case class Fixture[U](query: TableQuery[_ <: Table[U]]) {
    def create: DBIOAction[Unit, NoStream, Effect.Schema] = query.schema.create
    def delete: DBIOAction[Int, NoStream, Effect.Write] = query.delete
  }

  /*** Represents the script to initialize the database, before creating tables */
  case class InitScript(createSql: String, dropSql: String) {
    private val ddl = DDL(createSql, dropSql)

    def create: DBIOAction[Unit, NoStream, Effect.Schema] = ddl.create
  }

}
