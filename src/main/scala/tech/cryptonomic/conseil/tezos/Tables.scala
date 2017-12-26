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
  lazy val schema: profile.SchemaDescription = Array(Ballots.schema, Blocks.schema, Delegations.schema, Endorsements.schema, FaucetTransactions.schema, OperationGroups.schema, Originations.schema, Proposals.schema, SeedNonceRevealations.schema, Transactions.schema).reduceLeft(_ ++ _)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Ballots
    *  @param ballotId Database column ballot_id SqlType(serial), AutoInc, PrimaryKey
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar)
    *  @param period Database column period SqlType(int4)
    *  @param proposal Database column proposal SqlType(varchar)
    *  @param ballot Database column ballot SqlType(varchar) */
  case class BallotsRow(ballotId: Int, operationGroupHash: String, period: Int, proposal: String, ballot: String)
  /** GetResult implicit for fetching BallotsRow objects using plain SQL queries */
  implicit def GetResultBallotsRow(implicit e0: GR[Int], e1: GR[String]): GR[BallotsRow] = GR{
    prs => import prs._
      BallotsRow.tupled((<<[Int], <<[String], <<[Int], <<[String], <<[String]))
  }
  /** Table description of table ballots. Objects of this class serve as prototypes for rows in queries. */
  class Ballots(_tableTag: Tag) extends profile.api.Table[BallotsRow](_tableTag, "ballots") {
    def * = (ballotId, operationGroupHash, period, proposal, ballot) <> (BallotsRow.tupled, BallotsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(ballotId), Rep.Some(operationGroupHash), Rep.Some(period), Rep.Some(proposal), Rep.Some(ballot)).shaped.<>({r=>import r._; _1.map(_=> BallotsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column ballot_id SqlType(serial), AutoInc, PrimaryKey */
    val ballotId: Rep[Int] = column[Int]("ballot_id", O.AutoInc, O.PrimaryKey)
    /** Database column operation_group_hash SqlType(varchar) */
    val operationGroupHash: Rep[String] = column[String]("operation_group_hash")
    /** Database column period SqlType(int4) */
    val period: Rep[Int] = column[Int]("period")
    /** Database column proposal SqlType(varchar) */
    val proposal: Rep[String] = column[String]("proposal")
    /** Database column ballot SqlType(varchar) */
    val ballot: Rep[String] = column[String]("ballot")

    /** Foreign key referencing OperationGroups (database name ballots_operation_group_hash_fkey) */
    lazy val operationGroupsFk = foreignKey("ballots_operation_group_hash_fkey", operationGroupHash, OperationGroups)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Ballots */
  lazy val Ballots = new TableQuery(tag => new Ballots(tag))

  /** Entity class storing rows of table Blocks
    *  @param netId Database column net_id SqlType(varchar)
    *  @param protocol Database column protocol SqlType(varchar)
    *  @param level Database column level SqlType(int4)
    *  @param proto Database column proto SqlType(int4)
    *  @param predecessor Database column predecessor SqlType(varchar)
    *  @param validationPass Database column validation_pass SqlType(int4)
    *  @param operationsHash Database column operations_hash SqlType(varchar)
    *  @param data Database column data SqlType(varchar)
    *  @param hash Database column hash SqlType(varchar)
    *  @param timestamp Database column timestamp SqlType(timestamp)
    *  @param fitness Database column fitness SqlType(varchar) */
  case class BlocksRow(netId: String, protocol: String, level: Int, proto: Int, predecessor: String, validationPass: Int, operationsHash: String, data: String, hash: String, timestamp: java.sql.Timestamp, fitness: String)
  /** GetResult implicit for fetching BlocksRow objects using plain SQL queries */
  implicit def GetResultBlocksRow(implicit e0: GR[String], e1: GR[Int], e2: GR[java.sql.Timestamp]): GR[BlocksRow] = GR{
    prs => import prs._
      BlocksRow.tupled((<<[String], <<[String], <<[Int], <<[Int], <<[String], <<[Int], <<[String], <<[String], <<[String], <<[java.sql.Timestamp], <<[String]))
  }
  /** Table description of table blocks. Objects of this class serve as prototypes for rows in queries. */
  class Blocks(_tableTag: Tag) extends profile.api.Table[BlocksRow](_tableTag, "blocks") {
    def * = (netId, protocol, level, proto, predecessor, validationPass, operationsHash, data, hash, timestamp, fitness) <> (BlocksRow.tupled, BlocksRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(netId), Rep.Some(protocol), Rep.Some(level), Rep.Some(proto), Rep.Some(predecessor), Rep.Some(validationPass), Rep.Some(operationsHash), Rep.Some(data), Rep.Some(hash), Rep.Some(timestamp), Rep.Some(fitness)).shaped.<>({r=>import r._; _1.map(_=> BlocksRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column net_id SqlType(varchar) */
    val netId: Rep[String] = column[String]("net_id")
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
    /** Database column data SqlType(varchar) */
    val data: Rep[String] = column[String]("data")
    /** Database column hash SqlType(varchar) */
    val hash: Rep[String] = column[String]("hash")
    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")
    /** Database column fitness SqlType(varchar) */
    val fitness: Rep[String] = column[String]("fitness")

    /** Foreign key referencing Blocks (database name blocks_predecessor_fkey) */
    lazy val blocksFk = foreignKey("blocks_predecessor_fkey", predecessor, Blocks)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Index over (hash) (database name blocks_hash_idx) */
    val index1 = index("blocks_hash_idx", hash)
    /** Uniqueness Index over (hash) (database name blocks_hash_key) */
    val index2 = index("blocks_hash_key", hash, unique=true)
  }
  /** Collection-like TableQuery object for table Blocks */
  lazy val Blocks = new TableQuery(tag => new Blocks(tag))

  /** Entity class storing rows of table Delegations
    *  @param delegationId Database column delegation_id SqlType(serial), AutoInc, PrimaryKey
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar)
    *  @param delegate Database column delegate SqlType(varchar) */
  case class DelegationsRow(delegationId: Int, operationGroupHash: String, delegate: String)
  /** GetResult implicit for fetching DelegationsRow objects using plain SQL queries */
  implicit def GetResultDelegationsRow(implicit e0: GR[Int], e1: GR[String]): GR[DelegationsRow] = GR{
    prs => import prs._
      DelegationsRow.tupled((<<[Int], <<[String], <<[String]))
  }
  /** Table description of table delegations. Objects of this class serve as prototypes for rows in queries. */
  class Delegations(_tableTag: Tag) extends profile.api.Table[DelegationsRow](_tableTag, "delegations") {
    def * = (delegationId, operationGroupHash, delegate) <> (DelegationsRow.tupled, DelegationsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(delegationId), Rep.Some(operationGroupHash), Rep.Some(delegate)).shaped.<>({r=>import r._; _1.map(_=> DelegationsRow.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column delegation_id SqlType(serial), AutoInc, PrimaryKey */
    val delegationId: Rep[Int] = column[Int]("delegation_id", O.AutoInc, O.PrimaryKey)
    /** Database column operation_group_hash SqlType(varchar) */
    val operationGroupHash: Rep[String] = column[String]("operation_group_hash")
    /** Database column delegate SqlType(varchar) */
    val delegate: Rep[String] = column[String]("delegate")

    /** Foreign key referencing OperationGroups (database name delegations_operation_group_hash_fkey) */
    lazy val operationGroupsFk = foreignKey("delegations_operation_group_hash_fkey", operationGroupHash, OperationGroups)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Delegations */
  lazy val Delegations = new TableQuery(tag => new Delegations(tag))

  /** Entity class storing rows of table Endorsements
    *  @param endorsementId Database column endorsement_id SqlType(serial), AutoInc, PrimaryKey
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param slot Database column slot SqlType(int4) */
  case class EndorsementsRow(endorsementId: Int, operationGroupHash: String, blockId: String, slot: Int)
  /** GetResult implicit for fetching EndorsementsRow objects using plain SQL queries */
  implicit def GetResultEndorsementsRow(implicit e0: GR[Int], e1: GR[String]): GR[EndorsementsRow] = GR{
    prs => import prs._
      EndorsementsRow.tupled((<<[Int], <<[String], <<[String], <<[Int]))
  }
  /** Table description of table endorsements. Objects of this class serve as prototypes for rows in queries. */
  class Endorsements(_tableTag: Tag) extends profile.api.Table[EndorsementsRow](_tableTag, "endorsements") {
    def * = (endorsementId, operationGroupHash, blockId, slot) <> (EndorsementsRow.tupled, EndorsementsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(endorsementId), Rep.Some(operationGroupHash), Rep.Some(blockId), Rep.Some(slot)).shaped.<>({r=>import r._; _1.map(_=> EndorsementsRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column endorsement_id SqlType(serial), AutoInc, PrimaryKey */
    val endorsementId: Rep[Int] = column[Int]("endorsement_id", O.AutoInc, O.PrimaryKey)
    /** Database column operation_group_hash SqlType(varchar) */
    val operationGroupHash: Rep[String] = column[String]("operation_group_hash")
    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")
    /** Database column slot SqlType(int4) */
    val slot: Rep[Int] = column[Int]("slot")

    /** Foreign key referencing Blocks (database name endorsements_block_id_fkey) */
    lazy val blocksFk = foreignKey("endorsements_block_id_fkey", blockId, Blocks)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing OperationGroups (database name endorsements_operation_group_hash_fkey) */
    lazy val operationGroupsFk = foreignKey("endorsements_operation_group_hash_fkey", operationGroupHash, OperationGroups)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Endorsements */
  lazy val Endorsements = new TableQuery(tag => new Endorsements(tag))

  /** Entity class storing rows of table FaucetTransactions
    *  @param faucetTransactionId Database column faucet_transaction_id SqlType(serial), AutoInc, PrimaryKey
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar)
    *  @param id Database column id SqlType(varchar)
    *  @param nonce Database column nonce SqlType(varchar) */
  case class FaucetTransactionsRow(faucetTransactionId: Int, operationGroupHash: String, id: String, nonce: String)
  /** GetResult implicit for fetching FaucetTransactionsRow objects using plain SQL queries */
  implicit def GetResultFaucetTransactionsRow(implicit e0: GR[Int], e1: GR[String]): GR[FaucetTransactionsRow] = GR{
    prs => import prs._
      FaucetTransactionsRow.tupled((<<[Int], <<[String], <<[String], <<[String]))
  }
  /** Table description of table faucet_transactions. Objects of this class serve as prototypes for rows in queries. */
  class FaucetTransactions(_tableTag: Tag) extends profile.api.Table[FaucetTransactionsRow](_tableTag, "faucet_transactions") {
    def * = (faucetTransactionId, operationGroupHash, id, nonce) <> (FaucetTransactionsRow.tupled, FaucetTransactionsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(faucetTransactionId), Rep.Some(operationGroupHash), Rep.Some(id), Rep.Some(nonce)).shaped.<>({r=>import r._; _1.map(_=> FaucetTransactionsRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column faucet_transaction_id SqlType(serial), AutoInc, PrimaryKey */
    val faucetTransactionId: Rep[Int] = column[Int]("faucet_transaction_id", O.AutoInc, O.PrimaryKey)
    /** Database column operation_group_hash SqlType(varchar) */
    val operationGroupHash: Rep[String] = column[String]("operation_group_hash")
    /** Database column id SqlType(varchar) */
    val id: Rep[String] = column[String]("id")
    /** Database column nonce SqlType(varchar) */
    val nonce: Rep[String] = column[String]("nonce")

    /** Foreign key referencing OperationGroups (database name faucet_transactions_operation_group_hash_fkey) */
    lazy val operationGroupsFk = foreignKey("faucet_transactions_operation_group_hash_fkey", operationGroupHash, OperationGroups)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table FaucetTransactions */
  lazy val FaucetTransactions = new TableQuery(tag => new FaucetTransactions(tag))

  /** Entity class storing rows of table OperationGroups
    *  @param hash Database column hash SqlType(varchar), PrimaryKey
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param branch Database column branch SqlType(varchar)
    *  @param source Database column source SqlType(varchar), Default(None)
    *  @param signature Database column signature SqlType(varchar), Default(None) */
  case class OperationGroupsRow(hash: String, blockId: String, branch: String, source: Option[String] = None, signature: Option[String] = None)
  /** GetResult implicit for fetching OperationGroupsRow objects using plain SQL queries */
  implicit def GetResultOperationGroupsRow(implicit e0: GR[String], e1: GR[Option[String]]): GR[OperationGroupsRow] = GR{
    prs => import prs._
      OperationGroupsRow.tupled((<<[String], <<[String], <<[String], <<?[String], <<?[String]))
  }
  /** Table description of table operation_groups. Objects of this class serve as prototypes for rows in queries. */
  class OperationGroups(_tableTag: Tag) extends profile.api.Table[OperationGroupsRow](_tableTag, "operation_groups") {
    def * = (hash, blockId, branch, source, signature) <> (OperationGroupsRow.tupled, OperationGroupsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(hash), Rep.Some(blockId), Rep.Some(branch), source, signature).shaped.<>({r=>import r._; _1.map(_=> OperationGroupsRow.tupled((_1.get, _2.get, _3.get, _4, _5)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column hash SqlType(varchar), PrimaryKey */
    val hash: Rep[String] = column[String]("hash", O.PrimaryKey)
    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")
    /** Database column branch SqlType(varchar) */
    val branch: Rep[String] = column[String]("branch")
    /** Database column source SqlType(varchar), Default(None) */
    val source: Rep[Option[String]] = column[Option[String]]("source", O.Default(None))
    /** Database column signature SqlType(varchar), Default(None) */
    val signature: Rep[Option[String]] = column[Option[String]]("signature", O.Default(None))

    /** Foreign key referencing Blocks (database name OperationGroups_block_id_fkey) */
    lazy val blocksFk = foreignKey("OperationGroups_block_id_fkey", blockId, Blocks)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table OperationGroups */
  lazy val OperationGroups = new TableQuery(tag => new OperationGroups(tag))

  /** Entity class storing rows of table Originations
    *  @param originationId Database column origination_id SqlType(serial), AutoInc, PrimaryKey
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar)
    *  @param managerpubkey Database column managerPubkey SqlType(varchar), Default(None)
    *  @param balance Database column balance SqlType(numeric), Default(None)
    *  @param spendable Database column spendable SqlType(bool), Default(None)
    *  @param delegatable Database column delegatable SqlType(bool), Default(None)
    *  @param delegate Database column delegate SqlType(varchar), Default(None)
    *  @param script Database column script SqlType(varchar), Default(None) */
  case class OriginationsRow(originationId: Int, operationGroupHash: String, managerpubkey: Option[String] = None, balance: Option[scala.math.BigDecimal] = None, spendable: Option[Boolean] = None, delegatable: Option[Boolean] = None, delegate: Option[String] = None, script: Option[String] = None)
  /** GetResult implicit for fetching OriginationsRow objects using plain SQL queries */
  implicit def GetResultOriginationsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[String]], e3: GR[Option[scala.math.BigDecimal]], e4: GR[Option[Boolean]]): GR[OriginationsRow] = GR{
    prs => import prs._
      OriginationsRow.tupled((<<[Int], <<[String], <<?[String], <<?[scala.math.BigDecimal], <<?[Boolean], <<?[Boolean], <<?[String], <<?[String]))
  }
  /** Table description of table originations. Objects of this class serve as prototypes for rows in queries. */
  class Originations(_tableTag: Tag) extends profile.api.Table[OriginationsRow](_tableTag, "originations") {
    def * = (originationId, operationGroupHash, managerpubkey, balance, spendable, delegatable, delegate, script) <> (OriginationsRow.tupled, OriginationsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(originationId), Rep.Some(operationGroupHash), managerpubkey, balance, spendable, delegatable, delegate, script).shaped.<>({r=>import r._; _1.map(_=> OriginationsRow.tupled((_1.get, _2.get, _3, _4, _5, _6, _7, _8)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column origination_id SqlType(serial), AutoInc, PrimaryKey */
    val originationId: Rep[Int] = column[Int]("origination_id", O.AutoInc, O.PrimaryKey)
    /** Database column operation_group_hash SqlType(varchar) */
    val operationGroupHash: Rep[String] = column[String]("operation_group_hash")
    /** Database column managerPubkey SqlType(varchar), Default(None) */
    val managerpubkey: Rep[Option[String]] = column[Option[String]]("managerPubkey", O.Default(None))
    /** Database column balance SqlType(numeric), Default(None) */
    val balance: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("balance", O.Default(None))
    /** Database column spendable SqlType(bool), Default(None) */
    val spendable: Rep[Option[Boolean]] = column[Option[Boolean]]("spendable", O.Default(None))
    /** Database column delegatable SqlType(bool), Default(None) */
    val delegatable: Rep[Option[Boolean]] = column[Option[Boolean]]("delegatable", O.Default(None))
    /** Database column delegate SqlType(varchar), Default(None) */
    val delegate: Rep[Option[String]] = column[Option[String]]("delegate", O.Default(None))
    /** Database column script SqlType(varchar), Default(None) */
    val script: Rep[Option[String]] = column[Option[String]]("script", O.Default(None))

    /** Foreign key referencing OperationGroups (database name originations_operation_group_hash_fkey) */
    lazy val operationGroupsFk = foreignKey("originations_operation_group_hash_fkey", operationGroupHash, OperationGroups)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Originations */
  lazy val Originations = new TableQuery(tag => new Originations(tag))

  /** Entity class storing rows of table Proposals
    *  @param proposalId Database column proposal_id SqlType(serial), AutoInc, PrimaryKey
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar)
    *  @param period Database column period SqlType(int4)
    *  @param proposal Database column proposal SqlType(varchar) */
  case class ProposalsRow(proposalId: Int, operationGroupHash: String, period: Int, proposal: String)
  /** GetResult implicit for fetching ProposalsRow objects using plain SQL queries */
  implicit def GetResultProposalsRow(implicit e0: GR[Int], e1: GR[String]): GR[ProposalsRow] = GR{
    prs => import prs._
      ProposalsRow.tupled((<<[Int], <<[String], <<[Int], <<[String]))
  }
  /** Table description of table proposals. Objects of this class serve as prototypes for rows in queries. */
  class Proposals(_tableTag: Tag) extends profile.api.Table[ProposalsRow](_tableTag, "proposals") {
    def * = (proposalId, operationGroupHash, period, proposal) <> (ProposalsRow.tupled, ProposalsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(proposalId), Rep.Some(operationGroupHash), Rep.Some(period), Rep.Some(proposal)).shaped.<>({r=>import r._; _1.map(_=> ProposalsRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column proposal_id SqlType(serial), AutoInc, PrimaryKey */
    val proposalId: Rep[Int] = column[Int]("proposal_id", O.AutoInc, O.PrimaryKey)
    /** Database column operation_group_hash SqlType(varchar) */
    val operationGroupHash: Rep[String] = column[String]("operation_group_hash")
    /** Database column period SqlType(int4) */
    val period: Rep[Int] = column[Int]("period")
    /** Database column proposal SqlType(varchar) */
    val proposal: Rep[String] = column[String]("proposal")

    /** Foreign key referencing OperationGroups (database name proposals_operation_group_hash_fkey) */
    lazy val operationGroupsFk = foreignKey("proposals_operation_group_hash_fkey", operationGroupHash, OperationGroups)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Proposals */
  lazy val Proposals = new TableQuery(tag => new Proposals(tag))

  /** Entity class storing rows of table SeedNonceRevealations
    *  @param seedNonnceRevealationId Database column seed_nonnce_revealation_id SqlType(serial), AutoInc, PrimaryKey
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar)
    *  @param level Database column level SqlType(int4)
    *  @param nonce Database column nonce SqlType(varchar) */
  case class SeedNonceRevealationsRow(seedNonnceRevealationId: Int, operationGroupHash: String, level: Int, nonce: String)
  /** GetResult implicit for fetching SeedNonceRevealationsRow objects using plain SQL queries */
  implicit def GetResultSeedNonceRevealationsRow(implicit e0: GR[Int], e1: GR[String]): GR[SeedNonceRevealationsRow] = GR{
    prs => import prs._
      SeedNonceRevealationsRow.tupled((<<[Int], <<[String], <<[Int], <<[String]))
  }
  /** Table description of table seed_nonce_revealations. Objects of this class serve as prototypes for rows in queries. */
  class SeedNonceRevealations(_tableTag: Tag) extends profile.api.Table[SeedNonceRevealationsRow](_tableTag, "seed_nonce_revealations") {
    def * = (seedNonnceRevealationId, operationGroupHash, level, nonce) <> (SeedNonceRevealationsRow.tupled, SeedNonceRevealationsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(seedNonnceRevealationId), Rep.Some(operationGroupHash), Rep.Some(level), Rep.Some(nonce)).shaped.<>({r=>import r._; _1.map(_=> SeedNonceRevealationsRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column seed_nonnce_revealation_id SqlType(serial), AutoInc, PrimaryKey */
    val seedNonnceRevealationId: Rep[Int] = column[Int]("seed_nonnce_revealation_id", O.AutoInc, O.PrimaryKey)
    /** Database column operation_group_hash SqlType(varchar) */
    val operationGroupHash: Rep[String] = column[String]("operation_group_hash")
    /** Database column level SqlType(int4) */
    val level: Rep[Int] = column[Int]("level")
    /** Database column nonce SqlType(varchar) */
    val nonce: Rep[String] = column[String]("nonce")

    /** Foreign key referencing OperationGroups (database name seed_nonce_revealations_operation_group_hash_fkey) */
    lazy val operationGroupsFk = foreignKey("seed_nonce_revealations_operation_group_hash_fkey", operationGroupHash, OperationGroups)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table SeedNonceRevealations */
  lazy val SeedNonceRevealations = new TableQuery(tag => new SeedNonceRevealations(tag))

  /** Entity class storing rows of table Transactions
    *  @param transactionId Database column transaction_id SqlType(serial), AutoInc, PrimaryKey
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar)
    *  @param amount Database column amount SqlType(numeric)
    *  @param destination Database column destination SqlType(varchar), Default(None)
    *  @param parameters Database column parameters SqlType(varchar), Default(None) */
  case class TransactionsRow(transactionId: Int, operationGroupHash: String, amount: scala.math.BigDecimal, destination: Option[String] = None, parameters: Option[String] = None)
  /** GetResult implicit for fetching TransactionsRow objects using plain SQL queries */
  implicit def GetResultTransactionsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[scala.math.BigDecimal], e3: GR[Option[String]]): GR[TransactionsRow] = GR{
    prs => import prs._
      TransactionsRow.tupled((<<[Int], <<[String], <<[scala.math.BigDecimal], <<?[String], <<?[String]))
  }
  /** Table description of table transactions. Objects of this class serve as prototypes for rows in queries. */
  class Transactions(_tableTag: Tag) extends profile.api.Table[TransactionsRow](_tableTag, "transactions") {
    def * = (transactionId, operationGroupHash, amount, destination, parameters) <> (TransactionsRow.tupled, TransactionsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(transactionId), Rep.Some(operationGroupHash), Rep.Some(amount), destination, parameters).shaped.<>({r=>import r._; _1.map(_=> TransactionsRow.tupled((_1.get, _2.get, _3.get, _4, _5)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column transaction_id SqlType(serial), AutoInc, PrimaryKey */
    val transactionId: Rep[Int] = column[Int]("transaction_id", O.AutoInc, O.PrimaryKey)
    /** Database column operation_group_hash SqlType(varchar) */
    val operationGroupHash: Rep[String] = column[String]("operation_group_hash")
    /** Database column amount SqlType(numeric) */
    val amount: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("amount")
    /** Database column destination SqlType(varchar), Default(None) */
    val destination: Rep[Option[String]] = column[Option[String]]("destination", O.Default(None))
    /** Database column parameters SqlType(varchar), Default(None) */
    val parameters: Rep[Option[String]] = column[Option[String]]("parameters", O.Default(None))

    /** Foreign key referencing OperationGroups (database name transactions_operation_group_hash_fkey) */
    lazy val operationGroupsFk = foreignKey("transactions_operation_group_hash_fkey", operationGroupHash, OperationGroups)(r => r.hash, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Transactions */
  lazy val Transactions = new TableQuery(tag => new Transactions(tag))
}
