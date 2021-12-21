package tech.cryptonomic.conseil.schema

import java.net.URI
import java.nio.file.Paths

import scala.concurrent.ExecutionContext

import cats.effect._
import cats.implicits._
import slick.codegen.SourceCodeGenerator
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.model.{Model, Table}

import cats.effect.unsafe.implicits.global

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

  io(uri = confUri, outputDir = None).unsafeRunSync()

  /** Essentially follow along what the [[SourceCodeGenerator.run]] does,
    * yet selecting only a number of tables, grouping the results in different
    * source files based on the namespace.
    *
    * This keeps the different blockchains' db models separated, if we follow the
    * namespace rule.
    *
    * @param uri will point to the configuration file, possibly in a subsection, using fragments
    * @param outputDir we actually define this inside the configuration, but we could change that
    * @return an IO effect to be executed via .unsafeRunSync() or similar calls
    */
  def io(
      uri: URI,
      outputDir: Option[String]
  ): IO[Unit] = {

    //we read configuration values
    val dc = DatabaseConfig.forURI[PostgresProfile](uri)
    val basePackage = dc.config.getString("codegen.package")
    val out = outputDir orElse Option(dc.config.getString("codegen.outputDir")) getOrElse "."

    /* Use cats.effect.Resource to get automatic-resource-management
     * Wrapping the operations on resource content with a `resource.use` call
     * will guarantee that those resources will be released after the execution.
     */
    val resources = Resource.fromAutoCloseable(IO(dc.db))

    resources.use { db =>
      /* First we define the steps:
       * - get the complete db model
       * - extract schemas/namespaces from the model
       * - filter the schemas we care about and write the sources for each
       */
      val getModel = IO.fromFuture(
        IO(db.run(dc.profile.createModel(None)(ExecutionContext.global).withPinnedSession))
      )

      val getSchemas = (model: Model) =>
        IO {
          val schemas = model.tables.groupBy(_.name.schema).collect {
            case (Some(schema), tables) if schema != "public" => (schema, tables)
          }
          println(
            s"""The database contains the following namespaces for model generation: ${schemas.keySet.mkString(", ")}"""
          )
          schemas
        }

      /* we combine multiple IO operations into a single sequence within the same IO wrapper with traverse */
      val writeSources = (schemas: List[(String, Seq[Table])]) =>
        schemas.traverse {
          case (schema, tables) =>
            IO(
              new SourceCodeGenerator(new Model(tables))
                .writeToFile(dc.profileName, s"$out$schema", s"$basePackage.$schema")
            ).start
        }.flatMap { fibers =>
          fibers.traverse(_.join)
        }.void

      /* sequence the operations, you might notice we use a custom context from cats.effect
       * to run the blocking filesystem operations on
       */
      for {
        model <- getModel
        schemas <- getSchemas(model)
        _ <- writeSources(schemas.toList)
      } yield ()

    }.handleErrorWith(error => IO(Console.err.println(s"Failed to generate the slick model: $error")))

  }
}
