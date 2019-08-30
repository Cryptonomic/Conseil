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
  lazy val schema: profile.SchemaDescription = Array(
    Accounts.schema,
    AccountsCheckpoint.schema,
    BalanceUpdates.schema,
    Ballots.schema,
    Blocks.schema,
    DelegatedContracts.schema,
    Delegates.schema,
    DelegatesCheckpoint.schema,
    Fees.schema,
    OperationGroups.schema,
    Operations.schema,
    Proposals.schema,
    Rolls.schema
  ).reduceLeft(_ ++ _)
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
    *  @param storage Database column storage SqlType(varchar), Default(None)
    *  @param balance Database column balance SqlType(numeric)
    *  @param blockLevel Database column block_level SqlType(numeric), Default(-1) */
  case class AccountsRow(
      accountId: String,
      blockId: String,
      manager: String,
      spendable: Boolean,
      delegateSetable: Boolean,
      delegateValue: Option[String] = None,
      counter: Int,
      script: Option[String] = None,
      storage: Option[String] = None,
      balance: scala.math.BigDecimal,
      blockLevel: scala.math.BigDecimal = scala.math.BigDecimal("-1")
  )

  /** GetResult implicit for fetching AccountsRow objects using plain SQL queries */
  implicit def GetResultAccountsRow(
      implicit e0: GR[String],
      e1: GR[Boolean],
      e2: GR[Option[String]],
      e3: GR[Int],
      e4: GR[scala.math.BigDecimal]
  ): GR[AccountsRow] = GR { prs =>
    import prs._
    AccountsRow.tupled(
      (
        <<[String],
        <<[String],
        <<[String],
        <<[Boolean],
        <<[Boolean],
        <<?[String],
        <<[Int],
        <<?[String],
        <<?[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal]
      )
    )
  }

  /** Table description of table accounts. Objects of this class serve as prototypes for rows in queries. */
  class Accounts(_tableTag: Tag) extends profile.api.Table[AccountsRow](_tableTag, "accounts") {
    def * =
      (
        accountId,
        blockId,
        manager,
        spendable,
        delegateSetable,
        delegateValue,
        counter,
        script,
        storage,
        balance,
        blockLevel
      ) <> (AccountsRow.tupled, AccountsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(accountId),
          Rep.Some(blockId),
          Rep.Some(manager),
          Rep.Some(spendable),
          Rep.Some(delegateSetable),
          delegateValue,
          Rep.Some(counter),
          script,
          storage,
          Rep.Some(balance),
          Rep.Some(blockLevel)
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(
            _ => AccountsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7.get, _8, _9, _10.get, _11.get))
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

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

    /** Database column storage SqlType(varchar), Default(None) */
    val storage: Rep[Option[String]] = column[Option[String]]("storage", O.Default(None))

    /** Database column balance SqlType(numeric) */
    val balance: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("balance")

    /** Database column block_level SqlType(numeric), Default(-1) */
    val blockLevel: Rep[scala.math.BigDecimal] =
      column[scala.math.BigDecimal]("block_level", O.Default(scala.math.BigDecimal("-1")))

    /** Foreign key referencing Blocks (database name accounts_block_id_fkey) */
    lazy val blocksFk = foreignKey("accounts_block_id_fkey", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

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
  implicit def GetResultAccountsCheckpointRow(implicit e0: GR[String], e1: GR[Int]): GR[AccountsCheckpointRow] = GR {
    prs =>
      import prs._
      AccountsCheckpointRow.tupled((<<[String], <<[String], <<[Int]))
  }

  /** Table description of table accounts_checkpoint. Objects of this class serve as prototypes for rows in queries. */
  class AccountsCheckpoint(_tableTag: Tag)
      extends profile.api.Table[AccountsCheckpointRow](_tableTag, "accounts_checkpoint") {
    def * = (accountId, blockId, blockLevel) <> (AccountsCheckpointRow.tupled, AccountsCheckpointRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(accountId), Rep.Some(blockId), Rep.Some(blockLevel))).shaped.<>(
        { r =>
          import r._; _1.map(_ => AccountsCheckpointRow.tupled((_1.get, _2.get, _3.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column account_id SqlType(varchar) */
    val accountId: Rep[String] = column[String]("account_id")

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column block_level SqlType(int4), Default(-1) */
    val blockLevel: Rep[Int] = column[Int]("block_level", O.Default(-1))

    /** Foreign key referencing Blocks (database name checkpoint_block_id_fkey) */
    lazy val blocksFk = foreignKey("checkpoint_block_id_fkey", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (accountId) (database name ix_accounts_checkpoint_account_id) */
    val index1 = index("ix_accounts_checkpoint_account_id", accountId)

    /** Index over (blockLevel) (database name ix_accounts_checkpoint_block_level) */
    val index2 = index("ix_accounts_checkpoint_block_level", blockLevel)
  }

  /** Collection-like TableQuery object for table AccountsCheckpoint */
  lazy val AccountsCheckpoint = new TableQuery(tag => new AccountsCheckpoint(tag))

  /** Entity class storing rows of table BalanceUpdates
    *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
    *  @param source Database column source SqlType(varchar)
    *  @param sourceId Database column source_id SqlType(int4), Default(None)
    *  @param sourceHash Database column source_hash SqlType(varchar), Default(None)
    *  @param kind Database column kind SqlType(varchar)
    *  @param contract Database column contract SqlType(varchar), Default(None)
    *  @param change Database column change SqlType(numeric)
    *  @param level Database column level SqlType(numeric), Default(None)
    *  @param delegate Database column delegate SqlType(varchar), Default(None)
    *  @param category Database column category SqlType(varchar), Default(None)
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar), Default(None) */
  case class BalanceUpdatesRow(
      id: Int,
      source: String,
      sourceId: Option[Int] = None,
      sourceHash: Option[String] = None,
      kind: String,
      contract: Option[String] = None,
      change: scala.math.BigDecimal,
      level: Option[scala.math.BigDecimal] = None,
      delegate: Option[String] = None,
      category: Option[String] = None,
      operationGroupHash: Option[String] = None
  )

  /** GetResult implicit for fetching BalanceUpdatesRow objects using plain SQL queries */
  implicit def GetResultBalanceUpdatesRow(
      implicit e0: GR[Int],
      e1: GR[String],
      e2: GR[Option[Int]],
      e3: GR[Option[String]],
      e4: GR[scala.math.BigDecimal],
      e5: GR[Option[scala.math.BigDecimal]]
  ): GR[BalanceUpdatesRow] = GR { prs =>
    import prs._
    BalanceUpdatesRow.tupled(
      (
        <<[Int],
        <<[String],
        <<?[Int],
        <<?[String],
        <<[String],
        <<?[String],
        <<[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[String],
        <<?[String],
        <<?[String]
      )
    )
  }

  /** Table description of table balance_updates. Objects of this class serve as prototypes for rows in queries. */
  class BalanceUpdates(_tableTag: Tag) extends profile.api.Table[BalanceUpdatesRow](_tableTag, "balance_updates") {
    def * =
      (id, source, sourceId, sourceHash, kind, contract, change, level, delegate, category, operationGroupHash) <> (BalanceUpdatesRow.tupled, BalanceUpdatesRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(id),
          Rep.Some(source),
          sourceId,
          sourceHash,
          Rep.Some(kind),
          contract,
          Rep.Some(change),
          level,
          delegate,
          category,
          operationGroupHash
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ => BalanceUpdatesRow.tupled((_1.get, _2.get, _3, _4, _5.get, _6, _7.get, _8, _9, _10, _11)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)

    /** Database column source SqlType(varchar) */
    val source: Rep[String] = column[String]("source")

    /** Database column source_id SqlType(int4), Default(None) */
    val sourceId: Rep[Option[Int]] = column[Option[Int]]("source_id", O.Default(None))

    /** Database column source_hash SqlType(varchar), Default(None) */
    val sourceHash: Rep[Option[String]] = column[Option[String]]("source_hash", O.Default(None))

    /** Database column kind SqlType(varchar) */
    val kind: Rep[String] = column[String]("kind")

    /** Database column contract SqlType(varchar), Default(None) */
    val contract: Rep[Option[String]] = column[Option[String]]("contract", O.Default(None))

    /** Database column change SqlType(numeric) */
    val change: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("change")

    /** Database column level SqlType(numeric), Default(None) */
    val level: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("level", O.Default(None))

    /** Database column delegate SqlType(varchar), Default(None) */
    val delegate: Rep[Option[String]] = column[Option[String]]("delegate", O.Default(None))

    /** Database column category SqlType(varchar), Default(None) */
    val category: Rep[Option[String]] = column[Option[String]]("category", O.Default(None))

    /** Database column operation_group_hash SqlType(varchar), Default(None) */
    val operationGroupHash: Rep[Option[String]] = column[Option[String]]("operation_group_hash", O.Default(None))
  }

  /** Collection-like TableQuery object for table BalanceUpdates */
  lazy val BalanceUpdates = new TableQuery(tag => new BalanceUpdates(tag))

  /** Entity class storing rows of table Ballots
    *  @param pkh Database column pkh SqlType(varchar)
    *  @param ballot Database column ballot SqlType(varchar)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param blockLevel Database column block_level SqlType(int4) */
  case class BallotsRow(pkh: String, ballot: String, blockId: String, blockLevel: Int)

  /** GetResult implicit for fetching BallotsRow objects using plain SQL queries */
  implicit def GetResultBallotsRow(implicit e0: GR[String], e1: GR[Int]): GR[BallotsRow] = GR { prs =>
    import prs._
    BallotsRow.tupled((<<[String], <<[String], <<[String], <<[Int]))
  }

  /** Table description of table ballots. Objects of this class serve as prototypes for rows in queries. */
  class Ballots(_tableTag: Tag) extends profile.api.Table[BallotsRow](_tableTag, "ballots") {
    def * = (pkh, ballot, blockId, blockLevel) <> (BallotsRow.tupled, BallotsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(pkh), Rep.Some(ballot), Rep.Some(blockId), Rep.Some(blockLevel))).shaped.<>({ r =>
        import r._; _1.map(_ => BallotsRow.tupled((_1.get, _2.get, _3.get, _4.get)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column pkh SqlType(varchar) */
    val pkh: Rep[String] = column[String]("pkh")

    /** Database column ballot SqlType(varchar) */
    val ballot: Rep[String] = column[String]("ballot")

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Foreign key referencing Blocks (database name ballot_block_id_fkey) */
    lazy val blocksFk = foreignKey("ballot_block_id_fkey", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )
  }

  /** Collection-like TableQuery object for table Ballots */
  lazy val Ballots = new TableQuery(tag => new Ballots(tag))

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
    *  @param operationsHash Database column operations_hash SqlType(varchar), Default(None)
    *  @param periodKind Database column period_kind SqlType(varchar), Default(None)
    *  @param currentExpectedQuorum Database column current_expected_quorum SqlType(int4), Default(None)
    *  @param activeProposal Database column active_proposal SqlType(varchar), Default(None)
    *  @param baker Database column baker SqlType(varchar), Default(None)
    *  @param nonceHash Database column nonce_hash SqlType(varchar), Default(None)
    *  @param consumedGas Database column consumed_gas SqlType(numeric), Default(None)
    *  @param metaLevel Database column meta_level SqlType(int4), Default(None)
    *  @param metaLevelPosition Database column meta_level_position SqlType(int4), Default(None)
    *  @param metaCycle Database column meta_cycle SqlType(int4), Default(None)
    *  @param metaCyclePosition Database column meta_cycle_position SqlType(int4), Default(None)
    *  @param metaVotingPeriod Database column meta_voting_period SqlType(int4), Default(None)
    *  @param metaVotingPeriodPosition Database column meta_voting_period_position SqlType(int4), Default(None)
    *  @param expectedCommitment Database column expected_commitment SqlType(bool), Default(None)
    *  @param priority Database column priority SqlType(int4), Default(None) */
  case class BlocksRow(
      level: Int,
      proto: Int,
      predecessor: String,
      timestamp: java.sql.Timestamp,
      validationPass: Int,
      fitness: String,
      context: Option[String] = None,
      signature: Option[String] = None,
      protocol: String,
      chainId: Option[String] = None,
      hash: String,
      operationsHash: Option[String] = None,
      periodKind: Option[String] = None,
      currentExpectedQuorum: Option[Int] = None,
      activeProposal: Option[String] = None,
      baker: Option[String] = None,
      nonceHash: Option[String] = None,
      consumedGas: Option[scala.math.BigDecimal] = None,
      metaLevel: Option[Int] = None,
      metaLevelPosition: Option[Int] = None,
      metaCycle: Option[Int] = None,
      metaCyclePosition: Option[Int] = None,
      metaVotingPeriod: Option[Int] = None,
      metaVotingPeriodPosition: Option[Int] = None,
      expectedCommitment: Option[Boolean] = None,
      priority: Option[Int] = None
  )

  /** GetResult implicit for fetching BlocksRow objects using plain SQL queries */
  implicit def GetResultBlocksRow(
      implicit e0: GR[Int],
      e1: GR[String],
      e2: GR[java.sql.Timestamp],
      e3: GR[Option[String]],
      e4: GR[Option[Int]],
      e5: GR[Option[scala.math.BigDecimal]],
      e6: GR[Option[Boolean]]
  ): GR[BlocksRow] = GR { prs =>
    import prs._
    BlocksRow(
      <<[Int],
      <<[Int],
      <<[String],
      <<[java.sql.Timestamp],
      <<[Int],
      <<[String],
      <<?[String],
      <<?[String],
      <<[String],
      <<?[String],
      <<[String],
      <<?[String],
      <<?[String],
      <<?[Int],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[scala.math.BigDecimal],
      <<?[Int],
      <<?[Int],
      <<?[Int],
      <<?[Int],
      <<?[Int],
      <<?[Int],
      <<?[Boolean],
      <<?[Int]
    )
  }

  /** Table description of table blocks. Objects of this class serve as prototypes for rows in queries. */
  class Blocks(_tableTag: Tag) extends profile.api.Table[BlocksRow](_tableTag, "blocks") {
    def * =
      (level :: proto :: predecessor :: timestamp :: validationPass :: fitness :: context :: signature :: protocol :: chainId :: hash :: operationsHash :: periodKind :: currentExpectedQuorum :: activeProposal :: baker :: nonceHash :: consumedGas :: metaLevel :: metaLevelPosition :: metaCycle :: metaCyclePosition :: metaVotingPeriod :: metaVotingPeriodPosition :: expectedCommitment :: priority :: HNil)
        .mapTo[BlocksRow]

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (Rep.Some(level) :: Rep.Some(proto) :: Rep.Some(predecessor) :: Rep.Some(timestamp) :: Rep.Some(validationPass) :: Rep
            .Some(fitness) :: context :: signature :: Rep.Some(protocol) :: chainId :: Rep.Some(hash) :: operationsHash :: periodKind :: currentExpectedQuorum :: activeProposal :: baker :: nonceHash :: consumedGas :: metaLevel :: metaLevelPosition :: metaCycle :: metaCyclePosition :: metaVotingPeriod :: metaVotingPeriodPosition :: expectedCommitment :: priority :: HNil).shaped
        .<>(
          r =>
            BlocksRow(
              r(0).asInstanceOf[Option[Int]].get,
              r(1).asInstanceOf[Option[Int]].get,
              r(2).asInstanceOf[Option[String]].get,
              r(3).asInstanceOf[Option[java.sql.Timestamp]].get,
              r(4).asInstanceOf[Option[Int]].get,
              r(5).asInstanceOf[Option[String]].get,
              r(6).asInstanceOf[Option[String]],
              r(7).asInstanceOf[Option[String]],
              r(8).asInstanceOf[Option[String]].get,
              r(9).asInstanceOf[Option[String]],
              r(10).asInstanceOf[Option[String]].get,
              r(11).asInstanceOf[Option[String]],
              r(12).asInstanceOf[Option[String]],
              r(13).asInstanceOf[Option[Int]],
              r(14).asInstanceOf[Option[String]],
              r(15).asInstanceOf[Option[String]],
              r(16).asInstanceOf[Option[String]],
              r(17).asInstanceOf[Option[scala.math.BigDecimal]],
              r(18).asInstanceOf[Option[Int]],
              r(19).asInstanceOf[Option[Int]],
              r(20).asInstanceOf[Option[Int]],
              r(21).asInstanceOf[Option[Int]],
              r(22).asInstanceOf[Option[Int]],
              r(23).asInstanceOf[Option[Int]],
              r(24).asInstanceOf[Option[Boolean]],
              r(25).asInstanceOf[Option[Int]]
            ),
          (_: Any) => throw new Exception("Inserting into ? projection not supported.")
        )

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

    /** Database column period_kind SqlType(varchar), Default(None) */
    val periodKind: Rep[Option[String]] = column[Option[String]]("period_kind", O.Default(None))

    /** Database column current_expected_quorum SqlType(int4), Default(None) */
    val currentExpectedQuorum: Rep[Option[Int]] = column[Option[Int]]("current_expected_quorum", O.Default(None))

    /** Database column active_proposal SqlType(varchar), Default(None) */
    val activeProposal: Rep[Option[String]] = column[Option[String]]("active_proposal", O.Default(None))

    /** Database column baker SqlType(varchar), Default(None) */
    val baker: Rep[Option[String]] = column[Option[String]]("baker", O.Default(None))

    /** Database column nonce_hash SqlType(varchar), Default(None) */
    val nonceHash: Rep[Option[String]] = column[Option[String]]("nonce_hash", O.Default(None))

    /** Database column consumed_gas SqlType(numeric), Default(None) */
    val consumedGas: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("consumed_gas", O.Default(None))

    /** Database column meta_level SqlType(int4), Default(None) */
    val metaLevel: Rep[Option[Int]] = column[Option[Int]]("meta_level", O.Default(None))

    /** Database column meta_level_position SqlType(int4), Default(None) */
    val metaLevelPosition: Rep[Option[Int]] = column[Option[Int]]("meta_level_position", O.Default(None))

    /** Database column meta_cycle SqlType(int4), Default(None) */
    val metaCycle: Rep[Option[Int]] = column[Option[Int]]("meta_cycle", O.Default(None))

    /** Database column meta_cycle_position SqlType(int4), Default(None) */
    val metaCyclePosition: Rep[Option[Int]] = column[Option[Int]]("meta_cycle_position", O.Default(None))

    /** Database column meta_voting_period SqlType(int4), Default(None) */
    val metaVotingPeriod: Rep[Option[Int]] = column[Option[Int]]("meta_voting_period", O.Default(None))

    /** Database column meta_voting_period_position SqlType(int4), Default(None) */
    val metaVotingPeriodPosition: Rep[Option[Int]] = column[Option[Int]]("meta_voting_period_position", O.Default(None))

    /** Database column expected_commitment SqlType(bool), Default(None) */
    val expectedCommitment: Rep[Option[Boolean]] = column[Option[Boolean]]("expected_commitment", O.Default(None))

    /** Database column priority SqlType(int4), Default(None) */
    val priority: Rep[Option[Int]] = column[Option[Int]]("priority", O.Default(None))

    /** Uniqueness Index over (hash) (database name blocks_hash_key) */
    val index1 = index("blocks_hash_key", hash :: HNil, unique = true)

    /** Index over (level) (database name ix_blocks_level) */
    val index2 = index("ix_blocks_level", level :: HNil)
  }

  /** Collection-like TableQuery object for table Blocks */
  lazy val Blocks = new TableQuery(tag => new Blocks(tag))

  /** Entity class storing rows of table DelegatedContracts
    *  @param accountId Database column account_id SqlType(varchar)
    *  @param delegateValue Database column delegate_value SqlType(varchar), Default(None) */
  case class DelegatedContractsRow(accountId: String, delegateValue: Option[String] = None)

  /** GetResult implicit for fetching DelegatedContractsRow objects using plain SQL queries */
  implicit def GetResultDelegatedContractsRow(
      implicit e0: GR[String],
      e1: GR[Option[String]]
  ): GR[DelegatedContractsRow] = GR { prs =>
    import prs._
    DelegatedContractsRow.tupled((<<[String], <<?[String]))
  }

  /** Table description of table delegated_contracts. Objects of this class serve as prototypes for rows in queries. */
  class DelegatedContracts(_tableTag: Tag)
      extends profile.api.Table[DelegatedContractsRow](_tableTag, "delegated_contracts") {
    def * = (accountId, delegateValue) <> (DelegatedContractsRow.tupled, DelegatedContractsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(accountId), delegateValue)).shaped.<>({ r =>
        import r._; _1.map(_ => DelegatedContractsRow.tupled((_1.get, _2)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column account_id SqlType(varchar) */
    val accountId: Rep[String] = column[String]("account_id")

    /** Database column delegate_value SqlType(varchar), Default(None) */
    val delegateValue: Rep[Option[String]] = column[Option[String]]("delegate_value", O.Default(None))

    /** Foreign key referencing Accounts (database name contracts_account_id_fkey) */
    lazy val accountsFk = foreignKey("contracts_account_id_fkey", accountId, Accounts)(
      r => r.accountId,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Foreign key referencing Delegates (database name contracts_delegate_pkh_fkey) */
    lazy val delegatesFk = foreignKey("contracts_delegate_pkh_fkey", delegateValue, Delegates)(
      r => Rep.Some(r.pkh),
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )
  }

  /** Collection-like TableQuery object for table DelegatedContracts */
  lazy val DelegatedContracts = new TableQuery(tag => new DelegatedContracts(tag))

  /** Entity class storing rows of table Delegates
    *  @param pkh Database column pkh SqlType(varchar), PrimaryKey
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param balance Database column balance SqlType(numeric), Default(None)
    *  @param frozenBalance Database column frozen_balance SqlType(numeric), Default(None)
    *  @param stakingBalance Database column staking_balance SqlType(numeric), Default(None)
    *  @param delegatedBalance Database column delegated_balance SqlType(numeric), Default(None)
    *  @param deactivated Database column deactivated SqlType(bool)
    *  @param gracePeriod Database column grace_period SqlType(int4)
    *  @param blockLevel Database column block_level SqlType(int4), Default(-1) */
  case class DelegatesRow(
      pkh: String,
      blockId: String,
      balance: Option[scala.math.BigDecimal] = None,
      frozenBalance: Option[scala.math.BigDecimal] = None,
      stakingBalance: Option[scala.math.BigDecimal] = None,
      delegatedBalance: Option[scala.math.BigDecimal] = None,
      deactivated: Boolean,
      gracePeriod: Int,
      blockLevel: Int = -1
  )

  /** GetResult implicit for fetching DelegatesRow objects using plain SQL queries */
  implicit def GetResultDelegatesRow(
      implicit e0: GR[String],
      e1: GR[Option[scala.math.BigDecimal]],
      e2: GR[Boolean],
      e3: GR[Int]
  ): GR[DelegatesRow] = GR { prs =>
    import prs._
    DelegatesRow.tupled(
      (
        <<[String],
        <<[String],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<[Boolean],
        <<[Int],
        <<[Int]
      )
    )
  }

  /** Table description of table delegates. Objects of this class serve as prototypes for rows in queries. */
  class Delegates(_tableTag: Tag) extends profile.api.Table[DelegatesRow](_tableTag, "delegates") {
    def * =
      (pkh, blockId, balance, frozenBalance, stakingBalance, delegatedBalance, deactivated, gracePeriod, blockLevel) <> (DelegatesRow.tupled, DelegatesRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(pkh),
          Rep.Some(blockId),
          balance,
          frozenBalance,
          stakingBalance,
          delegatedBalance,
          Rep.Some(deactivated),
          Rep.Some(gracePeriod),
          Rep.Some(blockLevel)
        )
      ).shaped.<>(
        { r =>
          import r._; _1.map(_ => DelegatesRow.tupled((_1.get, _2.get, _3, _4, _5, _6, _7.get, _8.get, _9.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column pkh SqlType(varchar), PrimaryKey */
    val pkh: Rep[String] = column[String]("pkh", O.PrimaryKey)

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column balance SqlType(numeric), Default(None) */
    val balance: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("balance", O.Default(None))

    /** Database column frozen_balance SqlType(numeric), Default(None) */
    val frozenBalance: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("frozen_balance", O.Default(None))

    /** Database column staking_balance SqlType(numeric), Default(None) */
    val stakingBalance: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("staking_balance", O.Default(None))

    /** Database column delegated_balance SqlType(numeric), Default(None) */
    val delegatedBalance: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("delegated_balance", O.Default(None))

    /** Database column deactivated SqlType(bool) */
    val deactivated: Rep[Boolean] = column[Boolean]("deactivated")

    /** Database column grace_period SqlType(int4) */
    val gracePeriod: Rep[Int] = column[Int]("grace_period")

    /** Database column block_level SqlType(int4), Default(-1) */
    val blockLevel: Rep[Int] = column[Int]("block_level", O.Default(-1))

    /** Foreign key referencing Blocks (database name delegates_block_id_fkey) */
    lazy val blocksFk = foreignKey("delegates_block_id_fkey", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )
  }

  /** Collection-like TableQuery object for table Delegates */
  lazy val Delegates = new TableQuery(tag => new Delegates(tag))

  /** Entity class storing rows of table DelegatesCheckpoint
    *  @param delegatePkh Database column delegate_pkh SqlType(varchar)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param blockLevel Database column block_level SqlType(int4), Default(-1) */
  case class DelegatesCheckpointRow(delegatePkh: String, blockId: String, blockLevel: Int = -1)

  /** GetResult implicit for fetching DelegatesCheckpointRow objects using plain SQL queries */
  implicit def GetResultDelegatesCheckpointRow(implicit e0: GR[String], e1: GR[Int]): GR[DelegatesCheckpointRow] = GR {
    prs =>
      import prs._
      DelegatesCheckpointRow.tupled((<<[String], <<[String], <<[Int]))
  }

  /** Table description of table delegates_checkpoint. Objects of this class serve as prototypes for rows in queries. */
  class DelegatesCheckpoint(_tableTag: Tag)
      extends profile.api.Table[DelegatesCheckpointRow](_tableTag, "delegates_checkpoint") {
    def * = (delegatePkh, blockId, blockLevel) <> (DelegatesCheckpointRow.tupled, DelegatesCheckpointRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(delegatePkh), Rep.Some(blockId), Rep.Some(blockLevel))).shaped.<>(
        { r =>
          import r._; _1.map(_ => DelegatesCheckpointRow.tupled((_1.get, _2.get, _3.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column delegate_pkh SqlType(varchar) */
    val delegatePkh: Rep[String] = column[String]("delegate_pkh")

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column block_level SqlType(int4), Default(-1) */
    val blockLevel: Rep[Int] = column[Int]("block_level", O.Default(-1))

    /** Foreign key referencing Blocks (database name delegate_checkpoint_block_id_fkey) */
    lazy val blocksFk = foreignKey("delegate_checkpoint_block_id_fkey", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (blockLevel) (database name ix_delegates_checkpoint_block_level) */
    val index1 = index("ix_delegates_checkpoint_block_level", blockLevel)
  }

  /** Collection-like TableQuery object for table DelegatesCheckpoint */
  lazy val DelegatesCheckpoint = new TableQuery(tag => new DelegatesCheckpoint(tag))

  /** Entity class storing rows of table Fees
    *  @param low Database column low SqlType(int4)
    *  @param medium Database column medium SqlType(int4)
    *  @param high Database column high SqlType(int4)
    *  @param timestamp Database column timestamp SqlType(timestamp)
    *  @param kind Database column kind SqlType(varchar) */
  case class FeesRow(low: Int, medium: Int, high: Int, timestamp: java.sql.Timestamp, kind: String)

  /** GetResult implicit for fetching FeesRow objects using plain SQL queries */
  implicit def GetResultFeesRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String]): GR[FeesRow] = GR {
    prs =>
      import prs._
      FeesRow.tupled((<<[Int], <<[Int], <<[Int], <<[java.sql.Timestamp], <<[String]))
  }

  /** Table description of table fees. Objects of this class serve as prototypes for rows in queries. */
  class Fees(_tableTag: Tag) extends profile.api.Table[FeesRow](_tableTag, "fees") {
    def * = (low, medium, high, timestamp, kind) <> (FeesRow.tupled, FeesRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(low), Rep.Some(medium), Rep.Some(high), Rep.Some(timestamp), Rep.Some(kind))).shaped.<>(
        { r =>
          import r._; _1.map(_ => FeesRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

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
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param blockLevel Database column block_level SqlType(int4) */
  case class OperationGroupsRow(
      protocol: String,
      chainId: Option[String] = None,
      hash: String,
      branch: String,
      signature: Option[String] = None,
      blockId: String,
      blockLevel: Int
  )

  /** GetResult implicit for fetching OperationGroupsRow objects using plain SQL queries */
  implicit def GetResultOperationGroupsRow(
      implicit e0: GR[String],
      e1: GR[Option[String]],
      e2: GR[Int]
  ): GR[OperationGroupsRow] = GR { prs =>
    import prs._
    OperationGroupsRow.tupled((<<[String], <<?[String], <<[String], <<[String], <<?[String], <<[String], <<[Int]))
  }

  /** Table description of table operation_groups. Objects of this class serve as prototypes for rows in queries. */
  class OperationGroups(_tableTag: Tag) extends profile.api.Table[OperationGroupsRow](_tableTag, "operation_groups") {
    def * =
      (protocol, chainId, hash, branch, signature, blockId, blockLevel) <> (OperationGroupsRow.tupled, OperationGroupsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(protocol),
          chainId,
          Rep.Some(hash),
          Rep.Some(branch),
          signature,
          Rep.Some(blockId),
          Rep.Some(blockLevel)
        )
      ).shaped.<>(
        { r =>
          import r._; _1.map(_ => OperationGroupsRow.tupled((_1.get, _2, _3.get, _4.get, _5, _6.get, _7.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

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

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Foreign key referencing Blocks (database name block) */
    lazy val blocksFk = foreignKey("block", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (blockLevel) (database name ix_operation_groups_block_level) */
    val index1 = index("ix_operation_groups_block_level", blockLevel)
  }

  /** Collection-like TableQuery object for table OperationGroups */
  lazy val OperationGroups = new TableQuery(tag => new OperationGroups(tag))

  /** Entity class storing rows of table Operations
    *  @param operationId Database column operation_id SqlType(serial), AutoInc, PrimaryKey
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar)
    *  @param kind Database column kind SqlType(varchar)
    *  @param level Database column level SqlType(int4), Default(None)
    *  @param delegate Database column delegate SqlType(varchar), Default(None)
    *  @param slots Database column slots SqlType(varchar), Default(None)
    *  @param nonce Database column nonce SqlType(varchar), Default(None)
    *  @param pkh Database column pkh SqlType(varchar), Default(None)
    *  @param secret Database column secret SqlType(varchar), Default(None)
    *  @param source Database column source SqlType(varchar), Default(None)
    *  @param fee Database column fee SqlType(numeric), Default(None)
    *  @param counter Database column counter SqlType(numeric), Default(None)
    *  @param gasLimit Database column gas_limit SqlType(numeric), Default(None)
    *  @param storageLimit Database column storage_limit SqlType(numeric), Default(None)
    *  @param publicKey Database column public_key SqlType(varchar), Default(None)
    *  @param amount Database column amount SqlType(numeric), Default(None)
    *  @param destination Database column destination SqlType(varchar), Default(None)
    *  @param parameters Database column parameters SqlType(varchar), Default(None)
    *  @param managerPubkey Database column manager_pubkey SqlType(varchar), Default(None)
    *  @param balance Database column balance SqlType(numeric), Default(None)
    *  @param spendable Database column spendable SqlType(bool), Default(None)
    *  @param delegatable Database column delegatable SqlType(bool), Default(None)
    *  @param script Database column script SqlType(varchar), Default(None)
    *  @param storage Database column storage SqlType(varchar), Default(None)
    *  @param status Database column status SqlType(varchar), Default(None)
    *  @param consumedGas Database column consumed_gas SqlType(numeric), Default(None)
    *  @param storageSize Database column storage_size SqlType(numeric), Default(None)
    *  @param paidStorageSizeDiff Database column paid_storage_size_diff SqlType(numeric), Default(None)
    *  @param originatedContracts Database column originated_contracts SqlType(varchar), Default(None)
    *  @param blockHash Database column block_hash SqlType(varchar)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param ballot Database column ballot SqlType(varchar), Default(None)
    *  @param internal Database column internal SqlType(bool)
    *  @param timestamp Database column timestamp SqlType(timestamp)
    *  @param proposal Database column proposal SqlType(varchar), Default(None)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param branch Database column branch SqlType(varchar), Default(None)
    *  @param numberOfSlots Database column number_of_slots SqlType(int4), Default(None)
    *  @param period Database column period SqlType(int4), Default(None) */
  case class OperationsRow(
      operationId: Int,
      operationGroupHash: String,
      kind: String,
      level: Option[Int] = None,
      delegate: Option[String] = None,
      slots: Option[String] = None,
      nonce: Option[String] = None,
      pkh: Option[String] = None,
      secret: Option[String] = None,
      source: Option[String] = None,
      fee: Option[scala.math.BigDecimal] = None,
      counter: Option[scala.math.BigDecimal] = None,
      gasLimit: Option[scala.math.BigDecimal] = None,
      storageLimit: Option[scala.math.BigDecimal] = None,
      publicKey: Option[String] = None,
      amount: Option[scala.math.BigDecimal] = None,
      destination: Option[String] = None,
      parameters: Option[String] = None,
      managerPubkey: Option[String] = None,
      balance: Option[scala.math.BigDecimal] = None,
      spendable: Option[Boolean] = None,
      delegatable: Option[Boolean] = None,
      script: Option[String] = None,
      storage: Option[String] = None,
      status: Option[String] = None,
      consumedGas: Option[scala.math.BigDecimal] = None,
      storageSize: Option[scala.math.BigDecimal] = None,
      paidStorageSizeDiff: Option[scala.math.BigDecimal] = None,
      originatedContracts: Option[String] = None,
      blockHash: String,
      blockLevel: Int,
      ballot: Option[String] = None,
      internal: Boolean,
      timestamp: java.sql.Timestamp,
      proposal: Option[String] = None,
      cycle: Option[Int] = None,
      branch: Option[String] = None,
      numberOfSlots: Option[Int] = None,
      period: Option[Int] = None
  )

  /** GetResult implicit for fetching OperationsRow objects using plain SQL queries */
  implicit def GetResultOperationsRow(
      implicit e0: GR[Int],
      e1: GR[String],
      e2: GR[Option[Int]],
      e3: GR[Option[String]],
      e4: GR[Option[scala.math.BigDecimal]],
      e5: GR[Option[Boolean]],
      e6: GR[Boolean],
      e7: GR[java.sql.Timestamp]
  ): GR[OperationsRow] = GR { prs =>
    import prs._
    OperationsRow(
      <<[Int],
      <<[String],
      <<[String],
      <<?[Int],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[scala.math.BigDecimal],
      <<?[scala.math.BigDecimal],
      <<?[scala.math.BigDecimal],
      <<?[scala.math.BigDecimal],
      <<?[String],
      <<?[scala.math.BigDecimal],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[scala.math.BigDecimal],
      <<?[Boolean],
      <<?[Boolean],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[scala.math.BigDecimal],
      <<?[scala.math.BigDecimal],
      <<?[scala.math.BigDecimal],
      <<?[String],
      <<[String],
      <<[Int],
      <<?[String],
      <<[Boolean],
      <<[java.sql.Timestamp],
      <<?[String],
      <<?[Int],
      <<?[String],
      <<?[Int],
      <<?[Int]
    )
  }

  /** Table description of table operations. Objects of this class serve as prototypes for rows in queries. */
  class Operations(_tableTag: Tag) extends profile.api.Table[OperationsRow](_tableTag, "operations") {
    def * =
      (operationId :: operationGroupHash :: kind :: level :: delegate :: slots :: nonce :: pkh :: secret :: source :: fee :: counter :: gasLimit :: storageLimit :: publicKey :: amount :: destination :: parameters :: managerPubkey :: balance :: spendable :: delegatable :: script :: storage :: status :: consumedGas :: storageSize :: paidStorageSizeDiff :: originatedContracts :: blockHash :: blockLevel :: ballot :: internal :: timestamp :: proposal :: cycle :: branch :: numberOfSlots :: period :: HNil)
        .mapTo[OperationsRow]

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (Rep.Some(operationId) :: Rep.Some(operationGroupHash) :: Rep.Some(kind) :: level :: delegate :: slots :: nonce :: pkh :: secret :: source :: fee :: counter :: gasLimit :: storageLimit :: publicKey :: amount :: destination :: parameters :: managerPubkey :: balance :: spendable :: delegatable :: script :: storage :: status :: consumedGas :: storageSize :: paidStorageSizeDiff :: originatedContracts :: Rep
            .Some(blockHash) :: Rep.Some(blockLevel) :: ballot :: Rep.Some(internal) :: Rep.Some(timestamp) :: proposal :: cycle :: branch :: numberOfSlots :: period :: HNil).shaped
        .<>(
          r =>
            OperationsRow(
              r(0).asInstanceOf[Option[Int]].get,
              r(1).asInstanceOf[Option[String]].get,
              r(2).asInstanceOf[Option[String]].get,
              r(3).asInstanceOf[Option[Int]],
              r(4).asInstanceOf[Option[String]],
              r(5).asInstanceOf[Option[String]],
              r(6).asInstanceOf[Option[String]],
              r(7).asInstanceOf[Option[String]],
              r(8).asInstanceOf[Option[String]],
              r(9).asInstanceOf[Option[String]],
              r(10).asInstanceOf[Option[scala.math.BigDecimal]],
              r(11).asInstanceOf[Option[scala.math.BigDecimal]],
              r(12).asInstanceOf[Option[scala.math.BigDecimal]],
              r(13).asInstanceOf[Option[scala.math.BigDecimal]],
              r(14).asInstanceOf[Option[String]],
              r(15).asInstanceOf[Option[scala.math.BigDecimal]],
              r(16).asInstanceOf[Option[String]],
              r(17).asInstanceOf[Option[String]],
              r(18).asInstanceOf[Option[String]],
              r(19).asInstanceOf[Option[scala.math.BigDecimal]],
              r(20).asInstanceOf[Option[Boolean]],
              r(21).asInstanceOf[Option[Boolean]],
              r(22).asInstanceOf[Option[String]],
              r(23).asInstanceOf[Option[String]],
              r(24).asInstanceOf[Option[String]],
              r(25).asInstanceOf[Option[scala.math.BigDecimal]],
              r(26).asInstanceOf[Option[scala.math.BigDecimal]],
              r(27).asInstanceOf[Option[scala.math.BigDecimal]],
              r(28).asInstanceOf[Option[String]],
              r(29).asInstanceOf[Option[String]].get,
              r(30).asInstanceOf[Option[Int]].get,
              r(31).asInstanceOf[Option[String]],
              r(32).asInstanceOf[Option[Boolean]].get,
              r(33).asInstanceOf[Option[java.sql.Timestamp]].get,
              r(34).asInstanceOf[Option[String]],
              r(35).asInstanceOf[Option[Int]],
              r(36).asInstanceOf[Option[String]],
              r(37).asInstanceOf[Option[Int]],
              r(38).asInstanceOf[Option[Int]]
            ),
          (_: Any) => throw new Exception("Inserting into ? projection not supported.")
        )

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
    val gasLimit: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("gas_limit", O.Default(None))

    /** Database column storage_limit SqlType(numeric), Default(None) */
    val storageLimit: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("storage_limit", O.Default(None))

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

    /** Database column storage SqlType(varchar), Default(None) */
    val storage: Rep[Option[String]] = column[Option[String]]("storage", O.Default(None))

    /** Database column status SqlType(varchar), Default(None) */
    val status: Rep[Option[String]] = column[Option[String]]("status", O.Default(None))

    /** Database column consumed_gas SqlType(numeric), Default(None) */
    val consumedGas: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("consumed_gas", O.Default(None))

    /** Database column storage_size SqlType(numeric), Default(None) */
    val storageSize: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("storage_size", O.Default(None))

    /** Database column paid_storage_size_diff SqlType(numeric), Default(None) */
    val paidStorageSizeDiff: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("paid_storage_size_diff", O.Default(None))

    /** Database column originated_contracts SqlType(varchar), Default(None) */
    val originatedContracts: Rep[Option[String]] = column[Option[String]]("originated_contracts", O.Default(None))

    /** Database column block_hash SqlType(varchar) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column ballot SqlType(varchar), Default(None) */
    val ballot: Rep[Option[String]] = column[Option[String]]("ballot", O.Default(None))

    /** Database column internal SqlType(bool) */
    val internal: Rep[Boolean] = column[Boolean]("internal")

    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")

    /** Database column proposal SqlType(varchar), Default(None) */
    val proposal: Rep[Option[String]] = column[Option[String]]("proposal", O.Default(None))

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Database column branch SqlType(varchar), Default(None) */
    val branch: Rep[Option[String]] = column[Option[String]]("branch", O.Default(None))

    /** Database column number_of_slots SqlType(int4), Default(None) */
    val numberOfSlots: Rep[Option[Int]] = column[Option[Int]]("number_of_slots", O.Default(None))

    /** Database column period SqlType(int4), Default(None) */
    val period: Rep[Option[Int]] = column[Option[Int]]("period", O.Default(None))

    /** Foreign key referencing Blocks (database name fk_blockhashes) */
    lazy val blocksFk = foreignKey("fk_blockhashes", blockHash :: HNil, Blocks)(
      r => r.hash :: HNil,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Foreign key referencing OperationGroups (database name fk_opgroups) */
    lazy val operationGroupsFk = foreignKey("fk_opgroups", operationGroupHash :: HNil, OperationGroups)(
      r => r.hash :: HNil,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (blockLevel) (database name ix_operations_block_level) */
    val index1 = index("ix_operations_block_level", blockLevel :: HNil)

    /** Index over (delegate) (database name ix_operations_delegate) */
    val index2 = index("ix_operations_delegate", delegate :: HNil)

    /** Index over (destination) (database name ix_operations_destination) */
    val index3 = index("ix_operations_destination", destination :: HNil)

    /** Index over (source) (database name ix_operations_source) */
    val index4 = index("ix_operations_source", source :: HNil)

    /** Index over (timestamp) (database name ix_operations_timestamp) */
    val index5 = index("ix_operations_timestamp", timestamp :: HNil)
  }

  /** Collection-like TableQuery object for table Operations */
  lazy val Operations = new TableQuery(tag => new Operations(tag))

  /** Entity class storing rows of table Proposals
    *  @param protocolHash Database column protocol_hash SqlType(varchar)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param supporters Database column supporters SqlType(int4), Default(None) */
  case class ProposalsRow(protocolHash: String, blockId: String, blockLevel: Int, supporters: Option[Int] = None)

  /** GetResult implicit for fetching ProposalsRow objects using plain SQL queries */
  implicit def GetResultProposalsRow(implicit e0: GR[String], e1: GR[Int], e2: GR[Option[Int]]): GR[ProposalsRow] = GR {
    prs =>
      import prs._
      ProposalsRow.tupled((<<[String], <<[String], <<[Int], <<?[Int]))
  }

  /** Table description of table proposals. Objects of this class serve as prototypes for rows in queries. */
  class Proposals(_tableTag: Tag) extends profile.api.Table[ProposalsRow](_tableTag, "proposals") {
    def * = (protocolHash, blockId, blockLevel, supporters) <> (ProposalsRow.tupled, ProposalsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(protocolHash), Rep.Some(blockId), Rep.Some(blockLevel), supporters)).shaped.<>({ r =>
        import r._; _1.map(_ => ProposalsRow.tupled((_1.get, _2.get, _3.get, _4)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column protocol_hash SqlType(varchar) */
    val protocolHash: Rep[String] = column[String]("protocol_hash")

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column supporters SqlType(int4), Default(None) */
    val supporters: Rep[Option[Int]] = column[Option[Int]]("supporters", O.Default(None))

    /** Foreign key referencing Blocks (database name proposal_block_id_fkey) */
    lazy val blocksFk = foreignKey("proposal_block_id_fkey", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (protocolHash) (database name ix_proposals_protocol) */
    val index1 = index("ix_proposals_protocol", protocolHash)
  }

  /** Collection-like TableQuery object for table Proposals */
  lazy val Proposals = new TableQuery(tag => new Proposals(tag))

  /** Entity class storing rows of table Rolls
    *  @param pkh Database column pkh SqlType(varchar)
    *  @param rolls Database column rolls SqlType(int4)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param blockLevel Database column block_level SqlType(int4) */
  case class RollsRow(pkh: String, rolls: Int, blockId: String, blockLevel: Int)

  /** GetResult implicit for fetching RollsRow objects using plain SQL queries */
  implicit def GetResultRollsRow(implicit e0: GR[String], e1: GR[Int]): GR[RollsRow] = GR { prs =>
    import prs._
    RollsRow.tupled((<<[String], <<[Int], <<[String], <<[Int]))
  }

  /** Table description of table rolls. Objects of this class serve as prototypes for rows in queries. */
  class Rolls(_tableTag: Tag) extends profile.api.Table[RollsRow](_tableTag, "rolls") {
    def * = (pkh, rolls, blockId, blockLevel) <> (RollsRow.tupled, RollsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(pkh), Rep.Some(rolls), Rep.Some(blockId), Rep.Some(blockLevel))).shaped.<>({ r =>
        import r._; _1.map(_ => RollsRow.tupled((_1.get, _2.get, _3.get, _4.get)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column pkh SqlType(varchar) */
    val pkh: Rep[String] = column[String]("pkh")

    /** Database column rolls SqlType(int4) */
    val rolls: Rep[Int] = column[Int]("rolls")

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Foreign key referencing Blocks (database name rolls_block_id_fkey) */
    lazy val blocksFk = foreignKey("rolls_block_id_fkey", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (blockLevel) (database name ix_rolls_block_level) */
    val index1 = index("ix_rolls_block_level", blockLevel)
  }

  /** Collection-like TableQuery object for table Rolls */
  lazy val Rolls = new TableQuery(tag => new Rolls(tag))
}
