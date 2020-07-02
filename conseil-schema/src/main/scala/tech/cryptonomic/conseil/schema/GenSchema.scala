package tech.cryptonomic.conseil.schema

import slick.codegen.SourceCodeGenerator
import java.net.URI
import java.nio.file.Paths

/**
  * Uses Slick's code-generation capabilities to infer code from Conseil database schema.
  * See http://slick.lightbend.com/doc/3.2.1/code-generation.html
  */
object GenSchema extends App {

  /* configuration section needed, both locally or in the classpath-provided file,
   * where the db connection definition is expected
   * It uses a URI fragment syntax, following the rules of the Slick Generator
   */
  val configSection = "#slickgen"

  val confUri = {
    val externallyProvided = args.headOption.map(
      confFile => Paths.get(confFile).toUri.resolve(configSection)
    )
    val classpathProvided = new URI(configSection) //this looks up in the standard application.conf as a resource
    println(s"""
    | Loading schema definitions from db configurations located at
    | - externally provided: $externallyProvided
    | - classpath fallback: application.conf$classpathProvided
    |
    | The source files will be generated under the folder /tmp/conseil-slick
    |
    | If you need to customize the database connection defaults, pass the file name as argument.
    | You're expected to provide a top-level hocon section named ${configSection.drop(1)}
    | An example looks like
    |
    | ${configSection.drop(1)} {
    |   profile: "slick.jdbc.PostgresProfile$$"
    |   db {
    |     driver: "org.portgresql.Driver"
    |     url: "jdbc:postgresql:<host>/<db-name>"
    |     user: ""
    |     password: ""
    |   }
    | }
    |
    | If passing a custom file definition as argument to the task, remember
    | you can <include required(classpath("application"))> to have everything
    | provided by default as per the local docker-compose definitions, and
    | only override what you need to.
    """.stripMargin)

    externallyProvided getOrElse classpathProvided
  }

  SourceCodeGenerator.run(uri = confUri, outputDir = None)

}
