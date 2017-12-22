package tech.cryptonomic.conseil.tezos
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object TezosTables extends {
  val profile = slick.jdbc.PostgresProfile
} with TezosTables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait TezosTables {
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
    *  @param blockId Database column block_id SqlType(int4), PrimaryKey
    *  @param netId Database column net_id SqlType(varchar)
    *  @param protocol Database column protocol SqlType(varchar)
    *  @param level Database column level SqlType(int4)
    *  @param proto Database column proto SqlType(int4)
    *  @param predecessor Database column predecessor SqlType(varchar), Default(None)
    *  @param timestamp Database column timestamp SqlType(timestamp)
    *  @param validationPass Database column validation_pass SqlType(int4)
    *  @param operationsHash Database column operations_hash SqlType(varchar)
    *  @param fitness1 Database column fitness_1 SqlType(int4)
    *  @param fitness2 Database column fitness_2 SqlType(int4)
    *  @param data Database column data SqlType(varchar)
    *  @param hash Database column hash SqlType(varchar) */
  case class BlocksRow(blockId: Int, netId: String, protocol: String, level: Int, proto: Int, predecessor: Option[String] = None, timestamp: java.sql.Timestamp, validationPass: Int, operationsHash: String, fitness1: Int, fitness2: Int, data: String, hash: String)
  /** GetResult implicit for fetching BlocksRow objects using plain SQL queries */
  implicit def GetResultBlocksRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[String]], e3: GR[java.sql.Timestamp]): GR[BlocksRow] = GR{
    prs => import prs._
      BlocksRow.tupled((<<[Int], <<[String], <<[String], <<[Int], <<[Int], <<?[String], <<[java.sql.Timestamp], <<[Int], <<[String], <<[Int], <<[Int], <<[String], <<[String]))
  }
  /** Table description of table blocks. Objects of this class serve as prototypes for rows in queries. */
  class Blocks(_tableTag: Tag) extends profile.api.Table[BlocksRow](_tableTag, "blocks") {
    def * = (blockId, netId, protocol, level, proto, predecessor, timestamp, validationPass, operationsHash, fitness1, fitness2, data, hash) <> (BlocksRow.tupled, BlocksRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(blockId), Rep.Some(netId), Rep.Some(protocol), Rep.Some(level), Rep.Some(proto), predecessor, Rep.Some(timestamp), Rep.Some(validationPass), Rep.Some(operationsHash), Rep.Some(fitness1), Rep.Some(fitness2), Rep.Some(data), Rep.Some(hash)).shaped.<>({r=>import r._; _1.map(_=> BlocksRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7.get, _8.get, _9.get, _10.get, _11.get, _12.get, _13.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column block_id SqlType(int4), PrimaryKey */
    val blockId: Rep[Int] = column[Int]("block_id", O.PrimaryKey)
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
    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")
    /** Database column validation_pass SqlType(int4) */
    val validationPass: Rep[Int] = column[Int]("validation_pass")
    /** Database column operations_hash SqlType(varchar) */
    val operationsHash: Rep[String] = column[String]("operations_hash")
    /** Database column fitness_1 SqlType(int4) */
    val fitness1: Rep[Int] = column[Int]("fitness_1")
    /** Database column fitness_2 SqlType(int4) */
    val fitness2: Rep[Int] = column[Int]("fitness_2")
    /** Database column data SqlType(varchar) */
    val data: Rep[String] = column[String]("data")
    /** Database column hash SqlType(varchar) */
    val hash: Rep[String] = column[String]("hash")

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
