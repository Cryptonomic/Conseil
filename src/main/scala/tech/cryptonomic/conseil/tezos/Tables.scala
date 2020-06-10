package tech.cryptonomic.conseil.tezos
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = slick.jdbc.PostgresProfile
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = profile.DDL(Nil, Nil)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema
}
