package tech.cryptonomic.conseil.scripts

import pureconfig.{loadConfig, CamelCase, ConfigFieldMapping}
import pureconfig.generic.auto._
import pureconfig.generic.ProductHint
import scala.concurrent.ExecutionContext.Implicits.global
import slick.codegen.SourceCodeGenerator
import tech.cryptonomic.conseil.tezos.TezosDatabaseOperations.CustomPostgresProfile

/**
  * Uses Slick's code-generation capabilities to infer code from Conseil database schema.
  * See http://slick.lightbend.com/doc/3.2.1/code-generation.html
  */
object GenSchema extends App {

  sealed trait DatabaseConfig {
    def databaseName: String

    def user: String

    def password: String
  }

  final case class PostgresConfig(databaseName: String, user: String, password: String) extends DatabaseConfig {
    lazy val url = s"jdbc:postgresql://localhost/${databaseName}"
    lazy val jdbcDriver = "org.postgresql.Driver"
    lazy val slickProfile = "slick.jdbc.PostgresProfile"
    lazy val `package` = "tech.cryptonomic.conseil.tezos"
    lazy val dest = "/tmp/slick"
  }

  //applies convention to uses CamelCase when reading config fields
  implicit def hint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

  val conseilDbConf = loadConfig[PostgresConfig](namespace = "conseil.db.properties")

  conseilDbConf.foreach { cfg =>
    println(s"Generating database Tables source file under ${cfg.dest}, in package ${cfg.`package`}")

    val db = CustomPostgresProfile.api.Database.forURL(
      cfg.url,
      driver = cfg.jdbcDriver,
      user = cfg.user,
      password = cfg.password
    )

    val model = db.run(CustomPostgresProfile.createModel(Some(CustomPostgresProfile.defaultTables)))

    val generatedCode = model.map(
      model =>
        new SourceCodeGenerator(model) {
          override def Table =
            table =>
              new Table(table) {
                override def Column = column => {
                  if (table.name.table == "operations" && column.name == "proposal") {
                    new Column(column.copy(tpe = "List[String]", nullable = true))
                  } else
                    new Column(column)
                }
              }
        }
    )

    generatedCode.failed.foreach(println)

    generatedCode.foreach { codegen =>
      codegen.writeToFile(
        cfg.slickProfile,
        cfg.dest,
        cfg.`package`,
        "Tables",
        "Tables.scala"
      )
    }

    Thread.sleep(20000)
  }
}
