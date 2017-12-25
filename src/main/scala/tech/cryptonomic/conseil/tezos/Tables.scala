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
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Blocks.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Blocks
    *  @param netId Database column net_id SqlType(varchar)
    *  @param protocol Database column protocol SqlType(varchar)
    *  @param level Database column level SqlType(int4)
    *  @param proto Database column proto SqlType(int4)
    *  @param predecessor Database column predecessor SqlType(varchar), Default(None)
    *  @param validationPass Database column validation_pass SqlType(int4)
    *  @param operationsHash Database column operations_hash SqlType(varchar)
    *  @param data Database column data SqlType(varchar)
    *  @param hash Database column hash SqlType(varchar)
    *  @param timestamp Database column timestamp SqlType(timestamp), Default(None)
    *  @param fitness1 Database column fitness_1 SqlType(varchar)
    *  @param fitness2 Database column fitness_2 SqlType(varchar) */
  case class BlocksRow(netId: String, protocol: String, level: Int, proto: Int, predecessor: Option[String] = None, validationPass: Int, operationsHash: String, data: String, hash: String, timestamp: Option[java.sql.Timestamp] = None, fitness1: String, fitness2: String)
  /** GetResult implicit for fetching BlocksRow objects using plain SQL queries */
  implicit def GetResultBlocksRow(implicit e0: GR[String], e1: GR[Int], e2: GR[Option[String]], e3: GR[Option[java.sql.Timestamp]]): GR[BlocksRow] = GR{
    prs => import prs._
      BlocksRow.tupled((<<[String], <<[String], <<[Int], <<[Int], <<?[String], <<[Int], <<[String], <<[String], <<[String], <<?[java.sql.Timestamp], <<[String], <<[String]))
  }
  /** Table description of table blocks. Objects of this class serve as prototypes for rows in queries. */
  class Blocks(_tableTag: Tag) extends profile.api.Table[BlocksRow](_tableTag, "blocks") {
    def * = (netId, protocol, level, proto, predecessor, validationPass, operationsHash, data, hash, timestamp, fitness1, fitness2) <> (BlocksRow.tupled, BlocksRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(netId), Rep.Some(protocol), Rep.Some(level), Rep.Some(proto), predecessor, Rep.Some(validationPass), Rep.Some(operationsHash), Rep.Some(data), Rep.Some(hash), timestamp, Rep.Some(fitness1), Rep.Some(fitness2)).shaped.<>({r=>import r._; _1.map(_=> BlocksRow.tupled((_1.get, _2.get, _3.get, _4.get, _5, _6.get, _7.get, _8.get, _9.get, _10, _11.get, _12.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column net_id SqlType(varchar) */
    val netId: Rep[String] = column[String]("net_id")
    /** Database column protocol SqlType(varchar) */
    val protocol: Rep[String] = column[String]("protocol")
    /** Database column level SqlType(int4) */
    val level: Rep[Int] = column[Int]("level")
    /** Database column proto SqlType(int4) */
    val proto: Rep[Int] = column[Int]("proto")
    /** Database column predecessor SqlType(varchar), Default(None) */
    val predecessor: Rep[Option[String]] = column[Option[String]]("predecessor", O.Default(None))
    /** Database column validation_pass SqlType(int4) */
    val validationPass: Rep[Int] = column[Int]("validation_pass")
    /** Database column operations_hash SqlType(varchar) */
    val operationsHash: Rep[String] = column[String]("operations_hash")
    /** Database column data SqlType(varchar) */
    val data: Rep[String] = column[String]("data")
    /** Database column hash SqlType(varchar) */
    val hash: Rep[String] = column[String]("hash")
    /** Database column timestamp SqlType(timestamp), Default(None) */
    val timestamp: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("timestamp", O.Default(None))
    /** Database column fitness_1 SqlType(varchar) */
    val fitness1: Rep[String] = column[String]("fitness_1")
    /** Database column fitness_2 SqlType(varchar) */
    val fitness2: Rep[String] = column[String]("fitness_2")

    /** Foreign key referencing Blocks (database name blocks_predecessor_fkey) */
    lazy val blocksFk = foreignKey("blocks_predecessor_fkey", predecessor, Blocks)(r => Rep.Some(r.hash), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Index over (hash) (database name blocks_hash_idx) */
    val index1 = index("blocks_hash_idx", hash)
    /** Uniqueness Index over (hash) (database name blocks_hash_key) */
    val index2 = index("blocks_hash_key", hash, unique=true)
  }
  /** Collection-like TableQuery object for table Blocks */
  lazy val Blocks = new TableQuery(tag => new Blocks(tag))
}
