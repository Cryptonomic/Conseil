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
  lazy val schema: profile.SchemaDescription = Accounts.schema ++ Blocks.schema ++ OperationGroups.schema ++ Operations.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Accounts
    *  @param accountId Database column account_id SqlType(varchar)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param manager Database column manager SqlType(varchar)
    *  @param spendable Database column spendable SqlType(bool)
    *  @param delegateSetable Database column delegate_setable SqlType(bool)
    *  @param delegateValue Database column delegate_value SqlType(varchar), Default(None)
    *  @param counter Database column counter SqlType(int4)
    *  @param script Database column script SqlType(varchar)
    *  @param balance Database column balance SqlType(numeric) */
  case class AccountsRow(accountId: String, blockId: String, manager: String, spendable: Boolean, delegateSetable: Boolean, delegateValue: Option[String] = None, counter: Int, script: Option[String], balance: scala.math.BigDecimal)
  /** GetResult implicit for fetching AccountsRow objects using plain SQL queries */
  implicit def GetResultAccountsRow(implicit e0: GR[String], e1: GR[Boolean], e2: GR[Option[String]], e3: GR[Int], e4: GR[scala.math.BigDecimal]): GR[AccountsRow] = GR{
    prs => import prs._
      AccountsRow.tupled((<<[String], <<[String], <<[String], <<[Boolean], <<[Boolean], <<?[String], <<[Int], <<?[String], <<[scala.math.BigDecimal]))
  }
  /** Table description of table accounts. Objects of this class serve as prototypes for rows in queries. */
  class Accounts(_tableTag: Tag) extends profile.api.Table[AccountsRow](_tableTag, "accounts") {
    def * = (accountId, blockId, manager, spendable, delegateSetable, delegateValue, counter, script, balance) <> (AccountsRow.tupled, AccountsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(accountId), Rep.Some(blockId), Rep.Some(manager), Rep.Some(spendable), Rep.Some(delegateSetable), delegateValue, Rep.Some(counter), script, Rep.Some(balance)).shaped.<>({r=>import r._; _1.map(_=> AccountsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7.get, _8, _9.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column account_id SqlType(varchar) */
    val accountId: Rep[String] = column[String]("account_id")
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
    /** Database column script SqlType(varchar) */
    val script: Rep[Option[String]] = column[Option[String]]("script")
    /** Database column balance SqlType(numeric) */
    val balance: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("balance")

    /** Primary key of Accounts (database name accounts_pkey) */
    val pk = primaryKey("accounts_pkey", (accountId, blockId))

    /** Foreign key referencing Blocks (database name accounts_block_id_fkey) */
    lazy val blocksFk = foreignKey("accounts_block_id_fkey", blockId, Blocks)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Accounts */
  lazy val Accounts = new TableQuery(tag => new Accounts(tag))

  /** Entity class storing rows of table Blocks
    *  @param chainId Database column chain_id SqlType(varchar)
    *  @param protocol Database column protocol SqlType(varchar)
    *  @param level Database column level SqlType(int4)
    *  @param proto Database column proto SqlType(int4)
    *  @param predecessor Database column predecessor SqlType(varchar)
    *  @param validationPass Database column validation_pass SqlType(int4)
    *  @param operationsHash Database column operations_hash SqlType(varchar)
    *  @param protocolData Database column protocol_data SqlType(varchar)
    *  @param hash Database column hash SqlType(varchar)
    *  @param timestamp Database column timestamp SqlType(timestamp)
    *  @param fitness Database column fitness SqlType(varchar)
    *  @param context Database column context SqlType(varchar), Default(None) */
  case class BlocksRow(chainId: String, protocol: String, level: Int, proto: Int, predecessor: String, validationPass: Int, operationsHash: String, protocolData: String, hash: String, timestamp: java.sql.Timestamp, fitness: String, context: Option[String] = None)
  /** GetResult implicit for fetching BlocksRow objects using plain SQL queries */
  implicit def GetResultBlocksRow(implicit e0: GR[String], e1: GR[Int], e2: GR[java.sql.Timestamp], e3: GR[Option[String]]): GR[BlocksRow] = GR{
    prs => import prs._
      BlocksRow.tupled((<<[String], <<[String], <<[Int], <<[Int], <<[String], <<[Int], <<[String], <<[String], <<[String], <<[java.sql.Timestamp], <<[String], <<?[String]))
  }
  /** Table description of table blocks. Objects of this class serve as prototypes for rows in queries. */
  class Blocks(_tableTag: Tag) extends profile.api.Table[BlocksRow](_tableTag, "blocks") {
    def * = (chainId, protocol, level, proto, predecessor, validationPass, operationsHash, protocolData, hash, timestamp, fitness, context) <> (BlocksRow.tupled, BlocksRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(chainId), Rep.Some(protocol), Rep.Some(level), Rep.Some(proto), Rep.Some(predecessor), Rep.Some(validationPass), Rep.Some(operationsHash), Rep.Some(protocolData), Rep.Some(hash), Rep.Some(timestamp), Rep.Some(fitness), context).shaped.<>({r=>import r._; _1.map(_=> BlocksRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get, _12)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column chain_id SqlType(varchar) */
    val chainId: Rep[String] = column[String]("chain_id")
    /** Database column protocol SqlType(varchar) */
    val protocol: Rep[String] = column[String]("protocol")
    /** Database column level SqlType(int4) */
    val level: Rep[Int] = column[Int]("level")
    /** Database column proto SqlType(int4) */
    val proto: Rep[Int] = column[Int]("proto")
    /** Database column predecessor SqlType(varchar) */
    val predecessor: Rep[String] = column[String]("predecessor")
    /** Database column validation_pass SqlType(int4) */
    val validationPass: Rep[Int] = column[Int]("validation_pass")
    /** Database column operations_hash SqlType(varchar) */
    val operationsHash: Rep[String] = column[String]("operations_hash")
    /** Database column protocol_data SqlType(varchar) */
    val protocolData: Rep[String] = column[String]("protocol_data")
    /** Database column hash SqlType(varchar) */
    val hash: Rep[String] = column[String]("hash")
    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")
    /** Database column fitness SqlType(varchar) */
    val fitness: Rep[String] = column[String]("fitness")
    /** Database column context SqlType(varchar), Default(None) */
    val context: Rep[Option[String]] = column[Option[String]]("context", O.Default(None))

    /** Foreign key referencing Blocks (database name blocks_predecessor_fkey) */
    lazy val blocksFk = foreignKey("blocks_predecessor_fkey", predecessor, Blocks)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Index over (hash) (database name blocks_hash_idx) */
    val index1 = index("blocks_hash_idx", hash)
    /** Uniqueness Index over (hash) (database name blocks_hash_key) */
    val index2 = index("blocks_hash_key", hash, unique=true)
  }
  /** Collection-like TableQuery object for table Blocks */
  lazy val Blocks = new TableQuery(tag => new Blocks(tag))

  /** Entity class storing rows of table OperationGroups
    *  @param hash Database column hash SqlType(varchar), PrimaryKey
    *  @param branch Database column branch SqlType(varchar)
    *  @param kind Database column kind SqlType(varchar), Default(None)
    *  @param block Database column block SqlType(varchar), Default(None)
    *  @param level Database column level SqlType(int4), Default(None)
    *  @param slots Database column slots SqlType(varchar), Default(None)
    *  @param signature Database column signature SqlType(varchar), Default(None)
    *  @param proposals Database column proposals SqlType(varchar), Default(None)
    *  @param period Database column period SqlType(numeric), Default(None)
    *  @param source Database column source SqlType(varchar), Default(None)
    *  @param proposal Database column proposal SqlType(varchar), Default(None)
    *  @param ballot Database column ballot SqlType(varchar), Default(None)
    *  @param chain Database column chain SqlType(varchar), Default(None)
    *  @param counter Database column counter SqlType(numeric), Default(None)
    *  @param fee Database column fee SqlType(varchar), Default(None) */
  case class OperationGroupsRow(hash: String, branch: String, kind: Option[String] = None, block: Option[String] = None, level: Option[Int] = None, slots: Option[String] = None, signature: Option[String] = None, proposals: Option[String] = None, period: Option[scala.math.BigDecimal] = None, source: Option[String] = None, proposal: Option[String] = None, ballot: Option[String] = None, chain: Option[String] = None, counter: Option[scala.math.BigDecimal] = None, fee: Option[String] = None)
  /** GetResult implicit for fetching OperationGroupsRow objects using plain SQL queries */
  implicit def GetResultOperationGroupsRow(implicit e0: GR[String], e1: GR[Option[String]], e2: GR[Option[Int]], e3: GR[Option[scala.math.BigDecimal]]): GR[OperationGroupsRow] = GR{
    prs => import prs._
      OperationGroupsRow.tupled((<<[String], <<[String], <<?[String], <<?[String], <<?[Int], <<?[String], <<?[String], <<?[String], <<?[scala.math.BigDecimal], <<?[String], <<?[String], <<?[String], <<?[String], <<?[scala.math.BigDecimal], <<?[String]))
  }
  /** Table description of table operation_groups. Objects of this class serve as prototypes for rows in queries. */
  class OperationGroups(_tableTag: Tag) extends profile.api.Table[OperationGroupsRow](_tableTag, "operation_groups") {
    def * = (hash, branch, kind, block, level, slots, signature, proposals, period, source, proposal, ballot, chain, counter, fee) <> (OperationGroupsRow.tupled, OperationGroupsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(hash), Rep.Some(branch), kind, block, level, slots, signature, proposals, period, source, proposal, ballot, chain, counter, fee).shaped.<>({r=>import r._; _1.map(_=> OperationGroupsRow.tupled((_1.get, _2.get, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column hash SqlType(varchar), PrimaryKey */
    val hash: Rep[String] = column[String]("hash", O.PrimaryKey)
    /** Database column branch SqlType(varchar) */
    val branch: Rep[String] = column[String]("branch")
    /** Database column kind SqlType(varchar), Default(None) */
    val kind: Rep[Option[String]] = column[Option[String]]("kind", O.Default(None))
    /** Database column block SqlType(varchar), Default(None) */
    val block: Rep[Option[String]] = column[Option[String]]("block", O.Default(None))
    /** Database column level SqlType(int4), Default(None) */
    val level: Rep[Option[Int]] = column[Option[Int]]("level", O.Default(None))
    /** Database column slots SqlType(varchar), Default(None) */
    val slots: Rep[Option[String]] = column[Option[String]]("slots", O.Default(None))
    /** Database column signature SqlType(varchar), Default(None) */
    val signature: Rep[Option[String]] = column[Option[String]]("signature", O.Default(None))
    /** Database column proposals SqlType(varchar), Default(None) */
    val proposals: Rep[Option[String]] = column[Option[String]]("proposals", O.Default(None))
    /** Database column period SqlType(numeric), Default(None) */
    val period: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("period", O.Default(None))
    /** Database column source SqlType(varchar), Default(None) */
    val source: Rep[Option[String]] = column[Option[String]]("source", O.Default(None))
    /** Database column proposal SqlType(varchar), Default(None) */
    val proposal: Rep[Option[String]] = column[Option[String]]("proposal", O.Default(None))
    /** Database column ballot SqlType(varchar), Default(None) */
    val ballot: Rep[Option[String]] = column[Option[String]]("ballot", O.Default(None))
    /** Database column chain SqlType(varchar), Default(None) */
    val chain: Rep[Option[String]] = column[Option[String]]("chain", O.Default(None))
    /** Database column counter SqlType(numeric), Default(None) */
    val counter: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("counter", O.Default(None))
    /** Database column fee SqlType(varchar), Default(None) */
    val fee: Rep[Option[String]] = column[Option[String]]("fee", O.Default(None))

    /** Foreign key referencing Blocks (database name block) */
    lazy val blocksFk = foreignKey("block", block, Blocks)(r => Rep.Some(r.hash), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table OperationGroups */
  lazy val OperationGroups = new TableQuery(tag => new OperationGroups(tag))

  /** Entity class storing rows of table Operations
    *  @param operationId Database column operation_id SqlType(bigserial), AutoInc, PrimaryKey
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar)
    *  @param opKind Database column op_kind SqlType(varchar)
    *  @param level Database column level SqlType(numeric), Default(None)
    *  @param nonce Database column nonce SqlType(varchar), Default(None)
    *  @param id Database column id SqlType(varchar), Default(None)
    *  @param publicKey Database column public_key SqlType(varchar), Default(None)
    *  @param amount Database column amount SqlType(varchar), Default(None)
    *  @param destination Database column destination SqlType(varchar), Default(None)
    *  @param parameters Database column parameters SqlType(varchar), Default(None)
    *  @param managerpubkey Database column managerPubKey SqlType(varchar), Default(None)
    *  @param balance Database column balance SqlType(varchar), Default(None)
    *  @param spendable Database column spendable SqlType(bool), Default(None)
    *  @param delegatable Database column delegatable SqlType(bool), Default(None)
    *  @param delegate Database column delegate SqlType(varchar), Default(None)
    *  @param script Database column script SqlType(varchar), Default(None) */
  case class OperationsRow(operationId: Long, operationGroupHash: String, opKind: String, level: Option[scala.math.BigDecimal] = None, nonce: Option[String] = None, id: Option[String] = None, publicKey: Option[String] = None, amount: Option[String] = None, destination: Option[String] = None, parameters: Option[String] = None, managerpubkey: Option[String] = None, balance: Option[String] = None, spendable: Option[Boolean] = None, delegatable: Option[Boolean] = None, delegate: Option[String] = None, script: Option[String] = None)
  /** GetResult implicit for fetching OperationsRow objects using plain SQL queries */
  implicit def GetResultOperationsRow(implicit e0: GR[Long], e1: GR[String], e2: GR[Option[scala.math.BigDecimal]], e3: GR[Option[String]], e4: GR[Option[Boolean]]): GR[OperationsRow] = GR{
    prs => import prs._
      OperationsRow.tupled((<<[Long], <<[String], <<[String], <<?[scala.math.BigDecimal], <<?[String], <<?[String], <<?[String], <<?[String], <<?[String], <<?[String], <<?[String], <<?[String], <<?[Boolean], <<?[Boolean], <<?[String], <<?[String]))
  }
  /** Table description of table operations. Objects of this class serve as prototypes for rows in queries. */
  class Operations(_tableTag: Tag) extends profile.api.Table[OperationsRow](_tableTag, "operations") {
    def * = (operationId, operationGroupHash, opKind, level, nonce, id, publicKey, amount, destination, parameters, managerpubkey, balance, spendable, delegatable, delegate, script) <> (OperationsRow.tupled, OperationsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(operationId), Rep.Some(operationGroupHash), Rep.Some(opKind), level, nonce, id, publicKey, amount, destination, parameters, managerpubkey, balance, spendable, delegatable, delegate, script).shaped.<>({r=>import r._; _1.map(_=> OperationsRow.tupled((_1.get, _2.get, _3.get, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column operation_id SqlType(bigserial), AutoInc, PrimaryKey */
    val operationId: Rep[Long] = column[Long]("operation_id", O.AutoInc, O.PrimaryKey)
    /** Database column operation_group_hash SqlType(varchar) */
    val operationGroupHash: Rep[String] = column[String]("operation_group_hash")
    /** Database column op_kind SqlType(varchar) */
    val opKind: Rep[String] = column[String]("op_kind")
    /** Database column level SqlType(numeric), Default(None) */
    val level: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("level", O.Default(None))
    /** Database column nonce SqlType(varchar), Default(None) */
    val nonce: Rep[Option[String]] = column[Option[String]]("nonce", O.Default(None))
    /** Database column id SqlType(varchar), Default(None) */
    val id: Rep[Option[String]] = column[Option[String]]("id", O.Default(None))
    /** Database column public_key SqlType(varchar), Default(None) */
    val publicKey: Rep[Option[String]] = column[Option[String]]("public_key", O.Default(None))
    /** Database column amount SqlType(varchar), Default(None) */
    val amount: Rep[Option[String]] = column[Option[String]]("amount", O.Default(None))
    /** Database column destination SqlType(varchar), Default(None) */
    val destination: Rep[Option[String]] = column[Option[String]]("destination", O.Default(None))
    /** Database column parameters SqlType(varchar), Default(None) */
    val parameters: Rep[Option[String]] = column[Option[String]]("parameters", O.Default(None))
    /** Database column managerPubKey SqlType(varchar), Default(None) */
    val managerpubkey: Rep[Option[String]] = column[Option[String]]("managerPubKey", O.Default(None))
    /** Database column balance SqlType(varchar), Default(None) */
    val balance: Rep[Option[String]] = column[Option[String]]("balance", O.Default(None))
    /** Database column spendable SqlType(bool), Default(None) */
    val spendable: Rep[Option[Boolean]] = column[Option[Boolean]]("spendable", O.Default(None))
    /** Database column delegatable SqlType(bool), Default(None) */
    val delegatable: Rep[Option[Boolean]] = column[Option[Boolean]]("delegatable", O.Default(None))
    /** Database column delegate SqlType(varchar), Default(None) */
    val delegate: Rep[Option[String]] = column[Option[String]]("delegate", O.Default(None))
    /** Database column script SqlType(varchar), Default(None) */
    val script: Rep[Option[String]] = column[Option[String]]("script", O.Default(None))

    /** Foreign key referencing OperationGroups (database name fk_opgroups) */
    lazy val operationGroupsFk = foreignKey("fk_opgroups", operationGroupHash, OperationGroups)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Operations */
  lazy val Operations = new TableQuery(tag => new Operations(tag))
}
