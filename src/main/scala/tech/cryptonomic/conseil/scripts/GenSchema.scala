package tech.cryptonomic.conseil.scripts

import pureconfig.{ConfigFieldMapping, CamelCase, loadConfig}
import pureconfig.generic.auto._
import pureconfig.generic.ProductHint

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

  val conseilDbConf = loadConfig[PostgresConfig](namespace = "conseildb.properties")

  conseilDbConf.foreach {
    cfg =>
      println(s"Generating database Tables source file under ${cfg.dest}, in package ${cfg.`package`}")
      slick.codegen.SourceCodeGenerator.main(
        Array(
          cfg.slickProfile,
          cfg.jdbcDriver,
          cfg.url,
          cfg.dest,
          cfg.`package`,
          cfg.user,
          cfg.password
        )
      )
  }

  conseilDbConf.left.foreach {
    failures =>
      sys.error(failures.toList.mkString("\n"))
  }

}
