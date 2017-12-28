package tech.cryptonomic.conseil.scripts

import com.typesafe.config.ConfigFactory

/**
  * Uses Slick's code-generation capabilities to infer code from Conseil database schema.
  * See http://slick.lightbend.com/doc/3.2.1/code-generation.html
  */
object GenSchema extends App {

  val conf = ConfigFactory.load

  val database = conf.getString("conseildb.properties.databaseName")
  val user = conf.getString("conseildb.properties.user")
  val password = conf.getString("conseildb.properties.password")
  val url = s"jdbc:postgresql://localhost/${database}" // connection info
  val jdbcDriver = "org.postgresql.Driver"
  val slickDriver = "slick.jdbc.PostgresProfile"
  val pkg = "tech.cryptonomic.conseil.tezos"

  slick.codegen.SourceCodeGenerator.main(
    Array(slickDriver, jdbcDriver, url, "/tmp/slick", pkg, s"${user}", s"${password}")
  )
}
