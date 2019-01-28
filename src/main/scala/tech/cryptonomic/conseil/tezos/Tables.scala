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
  import slick.collection.heterogeneous._
  import slick.collection.heterogeneous.syntax._
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Array(Accounts.schema, AccountsCheckpoint.schema, Blocks.schema, Fees.schema, OperationGroups.schema, Operations.schema).reduceLeft(_ ++ _)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Accounts
   *  @param accountId Database column account_id SqlType(varchar), PrimaryKey
   *  @param blockId Database column block_id SqlType(varchar)
   *  @param manager Database column manager SqlType(varchar)
   *  @param spendable Database column spendable SqlType(bool)
   *  @param delegateSetable Database column delegate_setable SqlType(bool)
   *  @param delegateValue Database column delegate_value SqlType(varchar), Default(None)
   *  @param counter Database column counter SqlType(int4)
   *  @param script Database column script SqlType(varchar), Default(None)
   *  @param balance Database column balance SqlType(numeric)
   *  @param blockLevel Database column block_level SqlType(numeric), Default(-1) */
  case class AccountsRow(accountId: String, blockId: String, manager: String, spendable: Boolean, delegateSetable: Boolean, delegateValue: Option[String] = None, counter: Int, script: Option[String] = None, balance: scala.math.BigDecimal, blockLevel: scala.math.BigDecimal = scala.math.BigDecimal("-1"))
  /** GetResult implicit for fetching AccountsRow objects using plain SQL queries */
  implicit def GetResultAccountsRow(implicit e0: GR[String], e1: GR[Boolean], e2: GR[Option[String]], e3: GR[Int], e4: GR[scala.math.BigDecimal]): GR[AccountsRow] = GR{
    prs => import prs._
    AccountsRow.tupled((<<[String], <<[String], <<[String], <<[Boolean], <<[Boolean], <<?[String], <<[Int], <<?[String], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal]))
  }
  /** Table description of table accounts. Objects of this class serve as prototypes for rows in queries. */
  class Accounts(_tableTag: Tag) extends profile.api.Table[AccountsRow](_tableTag, "accounts") {
    def * = (accountId, blockId, manager, spendable, delegateSetable, delegateValue, counter, script, balance, blockLevel) <> (AccountsRow.tupled, AccountsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(accountId), Rep.Some(blockId), Rep.Some(manager), Rep.Some(spendable), Rep.Some(delegateSetable), delegateValue, Rep.Some(counter), script, Rep.Some(balance), Rep.Some(blockLevel)).shaped.<>({r=>import r._; _1.map(_=> AccountsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7.get, _8, _9.get, _10.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column account_id SqlType(varchar), PrimaryKey */
    val accountId: Rep[String] = column[String]("account_id", O.PrimaryKey)
    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")
    /** Database column manager SqlType(varchar) */
    val manager: Rep[String] = column[String]("manager")
    /** Database column spendable SqlType(bool) */
    val spendable: Rep[Boolean] = column[Boolean]("spendable")
    /** Database column delegate_setable SqlType(bool) */
    val delegateSetable: Rep[Boolean] = column[Boolean]("delegate_setable")
    /** Database column delegate_value SqlType(varchar), Default(None) */
    val delegateValue: Rep[Option[String]] = column[Option[String]]("delegate_value", O.Default(None))
    /** Database column counter SqlType(int4) */
    val counter: Rep[Int] = column[Int]("counter")
    /** Database column script SqlType(varchar), Default(None) */
    val script: Rep[Option[String]] = column[Option[String]]("script", O.Default(None))
    /** Database column balance SqlType(numeric) */
    val balance: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("balance")
    /** Database column block_level SqlType(numeric), Default(-1) */
    val blockLevel: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("block_level", O.Default(scala.math.BigDecimal("-1")))

    /** Foreign key referencing Blocks (database name accounts_block_id_fkey) */
    lazy val blocksFk = foreignKey("accounts_block_id_fkey", blockId, Blocks)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Index over (blockLevel) (database name ix_accounts_block_level) */
    val index1 = index("ix_accounts_block_level", blockLevel)
    /** Index over (manager) (database name ix_accounts_manager) */
    val index2 = index("ix_accounts_manager", manager)
  }
  /** Collection-like TableQuery object for table Accounts */
  lazy val Accounts = new TableQuery(tag => new Accounts(tag))

  /** Entity class storing rows of table AccountsCheckpoint
   *  @param accountId Database column account_id SqlType(varchar)
   *  @param blockId Database column block_id SqlType(varchar)
   *  @param blockLevel Database column block_level SqlType(int4), Default(-1) */
  case class AccountsCheckpointRow(accountId: String, blockId: String, blockLevel: Int = -1)
  /** GetResult implicit for fetching AccountsCheckpointRow objects using plain SQL queries */
  implicit def GetResultAccountsCheckpointRow(implicit e0: GR[String], e1: GR[Int]): GR[AccountsCheckpointRow] = GR{
    prs => import prs._
    AccountsCheckpointRow.tupled((<<[String], <<[String], <<[Int]))
  }
  /** Table description of table accounts_checkpoint. Objects of this class serve as prototypes for rows in queries. */
  class AccountsCheckpoint(_tableTag: Tag) extends profile.api.Table[AccountsCheckpointRow](_tableTag, "accounts_checkpoint") {
    def * = (accountId, blockId, blockLevel) <> (AccountsCheckpointRow.tupled, AccountsCheckpointRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(accountId), Rep.Some(blockId), Rep.Some(blockLevel)).shaped.<>({r=>import r._; _1.map(_=> AccountsCheckpointRow.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column account_id SqlType(varchar) */
    val accountId: Rep[String] = column[String]("account_id")
    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")
    /** Database column block_level SqlType(int4), Default(-1) */
    val blockLevel: Rep[Int] = column[Int]("block_level", O.Default(-1))

    /** Foreign key referencing Blocks (database name checkpoint_block_id_fkey) */
    lazy val blocksFk = foreignKey("checkpoint_block_id_fkey", blockId, Blocks)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Index over (blockLevel) (database name ix_accounts_checkpoint_block_level) */
    val index1 = index("ix_accounts_checkpoint_block_level", blockLevel)
  }
  /** Collection-like TableQuery object for table AccountsCheckpoint */
  lazy val AccountsCheckpoint = new TableQuery(tag => new AccountsCheckpoint(tag))

  /** Entity class storing rows of table Blocks
   *  @param level Database column level SqlType(int4)
   *  @param proto Database column proto SqlType(int4)
   *  @param predecessor Database column predecessor SqlType(varchar)
   *  @param timestamp Database column timestamp SqlType(timestamp)
   *  @param validationPass Database column validation_pass SqlType(int4)
   *  @param fitness Database column fitness SqlType(varchar)
   *  @param context Database column context SqlType(varchar), Default(None)
   *  @param signature Database column signature SqlType(varchar), Default(None)
   *  @param protocol Database column protocol SqlType(varchar)
   *  @param chainId Database column chain_id SqlType(varchar), Default(None)
   *  @param hash Database column hash SqlType(varchar)
   *  @param operationsHash Database column operations_hash SqlType(varchar), Default(None) */
  case class BlocksRow(level: Int, proto: Int, predecessor: String, timestamp: java.sql.Timestamp, validationPass: Int, fitness: String, context: Option[String] = None, signature: Option[String] = None, protocol: String, chainId: Option[String] = None, hash: String, operationsHash: Option[String] = None)
  /** GetResult implicit for fetching BlocksRow objects using plain SQL queries */
  implicit def GetResultBlocksRow(implicit e0: GR[Int], e1: GR[String], e2: GR[java.sql.Timestamp], e3: GR[Option[String]]): GR[BlocksRow] = GR{
    prs => import prs._
    BlocksRow.tupled((<<[Int], <<[Int], <<[String], <<[java.sql.Timestamp], <<[Int], <<[String], <<?[String], <<?[String], <<[String], <<?[String], <<[String], <<?[String]))
  }
  /** Table description of table blocks. Objects of this class serve as prototypes for rows in queries. */
  class Blocks(_tableTag: Tag) extends profile.api.Table[BlocksRow](_tableTag, "blocks") {
    def * = (level, proto, predecessor, timestamp, validationPass, fitness, context, signature, protocol, chainId, hash, operationsHash) <> (BlocksRow.tupled, BlocksRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(level), Rep.Some(proto), Rep.Some(predecessor), Rep.Some(timestamp), Rep.Some(validationPass), Rep.Some(fitness), context, signature, Rep.Some(protocol), chainId, Rep.Some(hash), operationsHash).shaped.<>({r=>import r._; _1.map(_=> BlocksRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7, _8, _9.get, _10, _11.get, _12)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column level SqlType(int4) */
    val level: Rep[Int] = column[Int]("level")
    /** Database column proto SqlType(int4) */
    val proto: Rep[Int] = column[Int]("proto")
    /** Database column predecessor SqlType(varchar) */
    val predecessor: Rep[String] = column[String]("predecessor")
    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")
    /** Database column validation_pass SqlType(int4) */
    val validationPass: Rep[Int] = column[Int]("validation_pass")
    /** Database column fitness SqlType(varchar) */
    val fitness: Rep[String] = column[String]("fitness")
    /** Database column context SqlType(varchar), Default(None) */
    val context: Rep[Option[String]] = column[Option[String]]("context", O.Default(None))
    /** Database column signature SqlType(varchar), Default(None) */
    val signature: Rep[Option[String]] = column[Option[String]]("signature", O.Default(None))
    /** Database column protocol SqlType(varchar) */
    val protocol: Rep[String] = column[String]("protocol")
    /** Database column chain_id SqlType(varchar), Default(None) */
    val chainId: Rep[Option[String]] = column[Option[String]]("chain_id", O.Default(None))
    /** Database column hash SqlType(varchar) */
    val hash: Rep[String] = column[String]("hash")
    /** Database column operations_hash SqlType(varchar), Default(None) */
    val operationsHash: Rep[Option[String]] = column[Option[String]]("operations_hash", O.Default(None))

    /** Uniqueness Index over (hash) (database name blocks_hash_key) */
    val index1 = index("blocks_hash_key", hash, unique=true)
    /** Index over (level) (database name ix_blocks_level) */
    val index2 = index("ix_blocks_level", level)
  }
  /** Collection-like TableQuery object for table Blocks */
  lazy val Blocks = new TableQuery(tag => new Blocks(tag))

  /** Entity class storing rows of table Fees
   *  @param low Database column low SqlType(int4)
   *  @param medium Database column medium SqlType(int4)
   *  @param high Database column high SqlType(int4)
   *  @param timestamp Database column timestamp SqlType(timestamp)
   *  @param kind Database column kind SqlType(varchar) */
  case class FeesRow(low: Int, medium: Int, high: Int, timestamp: java.sql.Timestamp, kind: String)
  /** GetResult implicit for fetching FeesRow objects using plain SQL queries */
  implicit def GetResultFeesRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String]): GR[FeesRow] = GR{
    prs => import prs._
    FeesRow.tupled((<<[Int], <<[Int], <<[Int], <<[java.sql.Timestamp], <<[String]))
  }
  /** Table description of table fees. Objects of this class serve as prototypes for rows in queries. */
  class Fees(_tableTag: Tag) extends profile.api.Table[FeesRow](_tableTag, "fees") {
    def * = (low, medium, high, timestamp, kind) <> (FeesRow.tupled, FeesRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(low), Rep.Some(medium), Rep.Some(high), Rep.Some(timestamp), Rep.Some(kind)).shaped.<>({r=>import r._; _1.map(_=> FeesRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column low SqlType(int4) */
    val low: Rep[Int] = column[Int]("low")
    /** Database column medium SqlType(int4) */
    val medium: Rep[Int] = column[Int]("medium")
    /** Database column high SqlType(int4) */
    val high: Rep[Int] = column[Int]("high")
    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")
    /** Database column kind SqlType(varchar) */
    val kind: Rep[String] = column[String]("kind")
  }
  /** Collection-like TableQuery object for table Fees */
  lazy val Fees = new TableQuery(tag => new Fees(tag))

  /** Entity class storing rows of table OperationGroups
   *  @param protocol Database column protocol SqlType(varchar)
   *  @param chainId Database column chain_id SqlType(varchar), Default(None)
   *  @param hash Database column hash SqlType(varchar), PrimaryKey
   *  @param branch Database column branch SqlType(varchar)
   *  @param signature Database column signature SqlType(varchar), Default(None)
   *  @param blockId Database column block_id SqlType(varchar) */
  case class OperationGroupsRow(protocol: String, chainId: Option[String] = None, hash: String, branch: String, signature: Option[String] = None, blockId: String)
  /** GetResult implicit for fetching OperationGroupsRow objects using plain SQL queries */
  implicit def GetResultOperationGroupsRow(implicit e0: GR[String], e1: GR[Option[String]]): GR[OperationGroupsRow] = GR{
    prs => import prs._
    OperationGroupsRow.tupled((<<[String], <<?[String], <<[String], <<[String], <<?[String], <<[String]))
  }
  /** Table description of table operation_groups. Objects of this class serve as prototypes for rows in queries. */
  class OperationGroups(_tableTag: Tag) extends profile.api.Table[OperationGroupsRow](_tableTag, "operation_groups") {
    def * = (protocol, chainId, hash, branch, signature, blockId) <> (OperationGroupsRow.tupled, OperationGroupsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(protocol), chainId, Rep.Some(hash), Rep.Some(branch), signature, Rep.Some(blockId)).shaped.<>({r=>import r._; _1.map(_=> OperationGroupsRow.tupled((_1.get, _2, _3.get, _4.get, _5, _6.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column protocol SqlType(varchar) */
    val protocol: Rep[String] = column[String]("protocol")
    /** Database column chain_id SqlType(varchar), Default(None) */
    val chainId: Rep[Option[String]] = column[Option[String]]("chain_id", O.Default(None))
    /** Database column hash SqlType(varchar), PrimaryKey */
    val hash: Rep[String] = column[String]("hash", O.PrimaryKey)
    /** Database column branch SqlType(varchar) */
    val branch: Rep[String] = column[String]("branch")
    /** Database column signature SqlType(varchar), Default(None) */
    val signature: Rep[Option[String]] = column[Option[String]]("signature", O.Default(None))
    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Foreign key referencing Blocks (database name block) */
    lazy val blocksFk = foreignKey("block", blockId, Blocks)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table OperationGroups */
  lazy val OperationGroups = new TableQuery(tag => new OperationGroups(tag))

  /** Row type of table Operations */
  type OperationsRow = HCons[Int,HCons[String,HCons[String,HCons[Option[Int],HCons[Option[String],HCons[Option[String],HCons[Option[String],HCons[Option[String],HCons[Option[String],HCons[Option[String],HCons[Option[scala.math.BigDecimal],HCons[Option[scala.math.BigDecimal],HCons[Option[scala.math.BigDecimal],HCons[Option[scala.math.BigDecimal],HCons[Option[String],HCons[Option[scala.math.BigDecimal],HCons[Option[String],HCons[Option[String],HCons[Option[String],HCons[Option[scala.math.BigDecimal],HCons[Option[Boolean],HCons[Option[Boolean],HCons[Option[String],HCons[Option[String],HCons[String,HCons[java.sql.Timestamp,HNil]]]]]]]]]]]]]]]]]]]]]]]]]]
  /** Constructor for OperationsRow providing default values if available in the database schema. */
  def OperationsRow(operationId: Int, operationGroupHash: String, kind: String, level: Option[Int] = None, delegate: Option[String] = None, slots: Option[String] = None, nonce: Option[String] = None, pkh: Option[String] = None, secret: Option[String] = None, source: Option[String] = None, fee: Option[scala.math.BigDecimal] = None, counter: Option[scala.math.BigDecimal] = None, gasLimit: Option[scala.math.BigDecimal] = None, storageLimit: Option[scala.math.BigDecimal] = None, publicKey: Option[String] = None, amount: Option[scala.math.BigDecimal] = None, destination: Option[String] = None, parameters: Option[String] = None, managerPubkey: Option[String] = None, balance: Option[scala.math.BigDecimal] = None, spendable: Option[Boolean] = None, delegatable: Option[Boolean] = None, script: Option[String] = None, status: Option[String] = None, blockHash: String, timestamp: java.sql.Timestamp): OperationsRow = {
    operationId :: operationGroupHash :: kind :: level :: delegate :: slots :: nonce :: pkh :: secret :: source :: fee :: counter :: gasLimit :: storageLimit :: publicKey :: amount :: destination :: parameters :: managerPubkey :: balance :: spendable :: delegatable :: script :: status :: blockHash :: timestamp :: HNil
  }
  /** GetResult implicit for fetching OperationsRow objects using plain SQL queries */
  implicit def GetResultOperationsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[Int]], e3: GR[Option[String]], e4: GR[Option[scala.math.BigDecimal]], e5: GR[Option[Boolean]], e6: GR[java.sql.Timestamp]): GR[OperationsRow] = GR{
    prs => import prs._
    <<[Int] :: <<[String] :: <<[String] :: <<?[Int] :: <<?[String] :: <<?[String] :: <<?[String] :: <<?[String] :: <<?[String] :: <<?[String] :: <<?[scala.math.BigDecimal] :: <<?[scala.math.BigDecimal] :: <<?[scala.math.BigDecimal] :: <<?[scala.math.BigDecimal] :: <<?[String] :: <<?[scala.math.BigDecimal] :: <<?[String] :: <<?[String] :: <<?[String] :: <<?[scala.math.BigDecimal] :: <<?[Boolean] :: <<?[Boolean] :: <<?[String] :: <<?[String] :: <<[String] :: <<[java.sql.Timestamp] :: HNil
  }
  /** Table description of table operations. Objects of this class serve as prototypes for rows in queries. */
  class Operations(_tableTag: Tag) extends profile.api.Table[OperationsRow](_tableTag, "operations") {
    def * = operationId :: operationGroupHash :: kind :: level :: delegate :: slots :: nonce :: pkh :: secret :: source :: fee :: counter :: gasLimit :: storageLimit :: publicKey :: amount :: destination :: parameters :: managerPubkey :: balance :: spendable :: delegatable :: script :: status :: blockHash :: timestamp :: HNil

    /** Database column operation_id SqlType(serial), AutoInc, PrimaryKey */
    val operationId: Rep[Int] = column[Int]("operation_id", O.AutoInc, O.PrimaryKey)
    /** Database column operation_group_hash SqlType(varchar) */
    val operationGroupHash: Rep[String] = column[String]("operation_group_hash")
    /** Database column kind SqlType(varchar) */
    val kind: Rep[String] = column[String]("kind")
    /** Database column level SqlType(int4), Default(None) */
    val level: Rep[Option[Int]] = column[Option[Int]]("level", O.Default(None))
    /** Database column delegate SqlType(varchar), Default(None) */
    val delegate: Rep[Option[String]] = column[Option[String]]("delegate", O.Default(None))
    /** Database column slots SqlType(varchar), Default(None) */
    val slots: Rep[Option[String]] = column[Option[String]]("slots", O.Default(None))
    /** Database column nonce SqlType(varchar), Default(None) */
    val nonce: Rep[Option[String]] = column[Option[String]]("nonce", O.Default(None))
    /** Database column pkh SqlType(varchar), Default(None) */
    val pkh: Rep[Option[String]] = column[Option[String]]("pkh", O.Default(None))
    /** Database column secret SqlType(varchar), Default(None) */
    val secret: Rep[Option[String]] = column[Option[String]]("secret", O.Default(None))
    /** Database column source SqlType(varchar), Default(None) */
    val source: Rep[Option[String]] = column[Option[String]]("source", O.Default(None))
    /** Database column fee SqlType(numeric), Default(None) */
    val fee: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("fee", O.Default(None))
    /** Database column counter SqlType(numeric), Default(None) */
    val counter: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("counter", O.Default(None))
    /** Database column gas_limit SqlType(numeric), Default(None) */
    val gasLimit: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("gas_limit", O.Default(None))
    /** Database column storage_limit SqlType(numeric), Default(None) */
    val storageLimit: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("storage_limit", O.Default(None))
    /** Database column public_key SqlType(varchar), Default(None) */
    val publicKey: Rep[Option[String]] = column[Option[String]]("public_key", O.Default(None))
    /** Database column amount SqlType(numeric), Default(None) */
    val amount: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("amount", O.Default(None))
    /** Database column destination SqlType(varchar), Default(None) */
    val destination: Rep[Option[String]] = column[Option[String]]("destination", O.Default(None))
    /** Database column parameters SqlType(varchar), Default(None) */
    val parameters: Rep[Option[String]] = column[Option[String]]("parameters", O.Default(None))
    /** Database column manager_pubkey SqlType(varchar), Default(None) */
    val managerPubkey: Rep[Option[String]] = column[Option[String]]("manager_pubkey", O.Default(None))
    /** Database column balance SqlType(numeric), Default(None) */
    val balance: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("balance", O.Default(None))
    /** Database column spendable SqlType(bool), Default(None) */
    val spendable: Rep[Option[Boolean]] = column[Option[Boolean]]("spendable", O.Default(None))
    /** Database column delegatable SqlType(bool), Default(None) */
    val delegatable: Rep[Option[Boolean]] = column[Option[Boolean]]("delegatable", O.Default(None))
    /** Database column script SqlType(varchar), Default(None) */
    val script: Rep[Option[String]] = column[Option[String]]("script", O.Default(None))
    /** Database column status SqlType(varchar), Default(None) */
    val status: Rep[Option[String]] = column[Option[String]]("status", O.Default(None))
    /** Database column block_hash SqlType(varchar) */
    val blockHash: Rep[String] = column[String]("block_hash")
    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")

    /** Foreign key referencing Blocks (database name fk_blockhashes) */
    lazy val blocksFk = foreignKey("fk_blockhashes", blockHash :: HNil, Blocks)(r => r.hash :: HNil, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing OperationGroups (database name fk_opgroups) */
    lazy val operationGroupsFk = foreignKey("fk_opgroups", operationGroupHash :: HNil, OperationGroups)(r => r.hash :: HNil, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Index over (destination) (database name ix_operations_destination) */
    val index1 = index("ix_operations_destination", destination :: HNil)
    /** Index over (source) (database name ix_operations_source) */
    val index2 = index("ix_operations_source", source :: HNil)
  }
  /** Collection-like TableQuery object for table Operations */
  lazy val Operations = new TableQuery(tag => new Operations(tag))
}
