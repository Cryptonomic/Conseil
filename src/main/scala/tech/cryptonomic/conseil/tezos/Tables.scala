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
    AccountsHistory.schema,
    BakingRights.schema,
    BalanceUpdates.schema,
    BigMapContents.schema,
    BigMaps.schema,
    Blocks.schema,
    Delegates.schema,
    DelegatesCheckpoint.schema,
    EndorsingRights.schema,
    Fees.schema,
    OperationGroups.schema,
    Operations.schema,
    OriginatedAccountMaps.schema,
    ProcessedChainEvents.schema,
    Rolls.schema
  ).reduceLeft(_ ++ _)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Accounts
    *  @param accountId Database column account_id SqlType(varchar), PrimaryKey
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param counter Database column counter SqlType(int4), Default(None)
    *  @param script Database column script SqlType(varchar), Default(None)
    *  @param storage Database column storage SqlType(varchar), Default(None)
    *  @param balance Database column balance SqlType(numeric)
    *  @param blockLevel Database column block_level SqlType(numeric), Default(-1)
    *  @param manager Database column manager SqlType(varchar), Default(None)
    *  @param spendable Database column spendable SqlType(bool), Default(None)
    *  @param delegateSetable Database column delegate_setable SqlType(bool), Default(None)
    *  @param delegateValue Database column delegate_value SqlType(varchar), Default(None)
    *  @param isBaker Database column is_baker SqlType(bool), Default(false) */
  case class AccountsRow(
      accountId: String,
      blockId: String,
      counter: Option[Int] = None,
      script: Option[String] = None,
      storage: Option[String] = None,
      balance: scala.math.BigDecimal,
      blockLevel: scala.math.BigDecimal = scala.math.BigDecimal("-1"),
      manager: Option[String] = None,
      spendable: Option[Boolean] = None,
      delegateSetable: Option[Boolean] = None,
      delegateValue: Option[String] = None,
      isBaker: Boolean = false
  )

  /** GetResult implicit for fetching AccountsRow objects using plain SQL queries */
  implicit def GetResultAccountsRow(
      implicit e0: GR[String],
      e1: GR[Option[Int]],
      e2: GR[Option[String]],
      e3: GR[scala.math.BigDecimal],
      e4: GR[Option[Boolean]],
      e5: GR[Boolean]
  ): GR[AccountsRow] = GR { prs =>
    import prs._
    AccountsRow.tupled(
      (
        <<[String],
        <<[String],
        <<?[Int],
        <<?[String],
        <<?[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal],
        <<?[String],
        <<?[Boolean],
        <<?[Boolean],
        <<?[String],
        <<[Boolean]
      )
    )
  }

  /** Table description of table accounts. Objects of this class serve as prototypes for rows in queries. */
  class Accounts(_tableTag: Tag) extends profile.api.Table[AccountsRow](_tableTag, Some("tezos"), "accounts") {
    def * =
      (
        accountId,
        blockId,
        counter,
        script,
        storage,
        balance,
        blockLevel,
        manager,
        spendable,
        delegateSetable,
        delegateValue,
        isBaker
      ) <> (AccountsRow.tupled, AccountsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(accountId),
          Rep.Some(blockId),
          counter,
          script,
          storage,
          Rep.Some(balance),
          Rep.Some(blockLevel),
          manager,
          spendable,
          delegateSetable,
          delegateValue,
          Rep.Some(isBaker)
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ => AccountsRow.tupled((_1.get, _2.get, _3, _4, _5, _6.get, _7.get, _8, _9, _10, _11, _12.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column account_id SqlType(varchar), PrimaryKey */
    val accountId: Rep[String] = column[String]("account_id", O.PrimaryKey)

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column counter SqlType(int4), Default(None) */
    val counter: Rep[Option[Int]] = column[Option[Int]]("counter", O.Default(None))

    /** Database column script SqlType(varchar), Default(None) */
    val script: Rep[Option[String]] = column[Option[String]]("script", O.Default(None))

    /** Database column storage SqlType(varchar), Default(None) */
    val storage: Rep[Option[String]] = column[Option[String]]("storage", O.Default(None))

    /** Database column balance SqlType(numeric) */
    val balance: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("balance")

    /** Database column block_level SqlType(numeric), Default(-1) */
    val blockLevel: Rep[scala.math.BigDecimal] =
      column[scala.math.BigDecimal]("block_level", O.Default(scala.math.BigDecimal("-1")))

    /** Database column manager SqlType(varchar), Default(None) */
    val manager: Rep[Option[String]] = column[Option[String]]("manager", O.Default(None))

    /** Database column spendable SqlType(bool), Default(None) */
    val spendable: Rep[Option[Boolean]] = column[Option[Boolean]]("spendable", O.Default(None))

    /** Database column delegate_setable SqlType(bool), Default(None) */
    val delegateSetable: Rep[Option[Boolean]] = column[Option[Boolean]]("delegate_setable", O.Default(None))

    /** Database column delegate_value SqlType(varchar), Default(None) */
    val delegateValue: Rep[Option[String]] = column[Option[String]]("delegate_value", O.Default(None))

    /** Database column is_baker SqlType(bool), Default(false) */
    val isBaker: Rep[Boolean] = column[Boolean]("is_baker", O.Default(false))

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
    *  @param blockLevel Database column block_level SqlType(int4), Default(-1)
    *  @param asof Database column asof SqlType(timestamptz)
    *  @param cycle Database column cycle SqlType(int4), Default(None) */
  case class AccountsCheckpointRow(
      accountId: String,
      blockId: String,
      blockLevel: Int = -1,
      asof: java.sql.Timestamp,
      cycle: Option[Int] = None
  )

  /** GetResult implicit for fetching AccountsCheckpointRow objects using plain SQL queries */
  implicit def GetResultAccountsCheckpointRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[java.sql.Timestamp],
      e3: GR[Option[Int]]
  ): GR[AccountsCheckpointRow] = GR { prs =>
    import prs._
    AccountsCheckpointRow.tupled((<<[String], <<[String], <<[Int], <<[java.sql.Timestamp], <<?[Int]))
  }

  /** Table description of table accounts_checkpoint. Objects of this class serve as prototypes for rows in queries. */
  class AccountsCheckpoint(_tableTag: Tag)
      extends profile.api.Table[AccountsCheckpointRow](_tableTag, Some("tezos"), "accounts_checkpoint") {
    def * =
      (accountId, blockId, blockLevel, asof, cycle) <> (AccountsCheckpointRow.tupled, AccountsCheckpointRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(accountId), Rep.Some(blockId), Rep.Some(blockLevel), Rep.Some(asof), cycle)).shaped.<>(
        { r =>
          import r._; _1.map(_ => AccountsCheckpointRow.tupled((_1.get, _2.get, _3.get, _4.get, _5)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column account_id SqlType(varchar) */
    val accountId: Rep[String] = column[String]("account_id")

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column block_level SqlType(int4), Default(-1) */
    val blockLevel: Rep[Int] = column[Int]("block_level", O.Default(-1))

    /** Database column asof SqlType(timestamptz) */
    val asof: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("asof")

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Index over (accountId) (database name ix_accounts_checkpoint_account_id) */
    val index1 = index("ix_accounts_checkpoint_account_id", accountId)

    /** Index over (blockLevel) (database name ix_accounts_checkpoint_block_level) */
    val index2 = index("ix_accounts_checkpoint_block_level", blockLevel)
  }

  /** Collection-like TableQuery object for table AccountsCheckpoint */
  lazy val AccountsCheckpoint = new TableQuery(tag => new AccountsCheckpoint(tag))

  /** Entity class storing rows of table AccountsHistory
    *  @param accountId Database column account_id SqlType(varchar)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param counter Database column counter SqlType(int4), Default(None)
    *  @param storage Database column storage SqlType(varchar), Default(None)
    *  @param balance Database column balance SqlType(numeric)
    *  @param blockLevel Database column block_level SqlType(numeric), Default(-1)
    *  @param delegateValue Database column delegate_value SqlType(varchar), Default(None)
    *  @param asof Database column asof SqlType(timestamp)
    *  @param isBaker Database column is_baker SqlType(bool), Default(false)
    *  @param cycle Database column cycle SqlType(int4), Default(None) */
  case class AccountsHistoryRow(
      accountId: String,
      blockId: String,
      counter: Option[Int] = None,
      storage: Option[String] = None,
      balance: scala.math.BigDecimal,
      blockLevel: scala.math.BigDecimal = scala.math.BigDecimal("-1"),
      delegateValue: Option[String] = None,
      asof: java.sql.Timestamp,
      isBaker: Boolean = false,
      cycle: Option[Int] = None
  )

  /** GetResult implicit for fetching AccountsHistoryRow objects using plain SQL queries */
  implicit def GetResultAccountsHistoryRow(
      implicit e0: GR[String],
      e1: GR[Option[Int]],
      e2: GR[Option[String]],
      e3: GR[scala.math.BigDecimal],
      e4: GR[java.sql.Timestamp],
      e5: GR[Boolean]
  ): GR[AccountsHistoryRow] = GR { prs =>
    import prs._
    AccountsHistoryRow.tupled(
      (
        <<[String],
        <<[String],
        <<?[Int],
        <<?[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal],
        <<?[String],
        <<[java.sql.Timestamp],
        <<[Boolean],
        <<?[Int]
      )
    )
  }

  /** Table description of table accounts_history. Objects of this class serve as prototypes for rows in queries. */
  class AccountsHistory(_tableTag: Tag)
      extends profile.api.Table[AccountsHistoryRow](_tableTag, Some("tezos"), "accounts_history") {
    def * =
      (accountId, blockId, counter, storage, balance, blockLevel, delegateValue, asof, isBaker, cycle) <> (AccountsHistoryRow.tupled, AccountsHistoryRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(accountId),
          Rep.Some(blockId),
          counter,
          storage,
          Rep.Some(balance),
          Rep.Some(blockLevel),
          delegateValue,
          Rep.Some(asof),
          Rep.Some(isBaker),
          cycle
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ => AccountsHistoryRow.tupled((_1.get, _2.get, _3, _4, _5.get, _6.get, _7, _8.get, _9.get, _10)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column account_id SqlType(varchar) */
    val accountId: Rep[String] = column[String]("account_id")

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column counter SqlType(int4), Default(None) */
    val counter: Rep[Option[Int]] = column[Option[Int]]("counter", O.Default(None))

    /** Database column storage SqlType(varchar), Default(None) */
    val storage: Rep[Option[String]] = column[Option[String]]("storage", O.Default(None))

    /** Database column balance SqlType(numeric) */
    val balance: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("balance")

    /** Database column block_level SqlType(numeric), Default(-1) */
    val blockLevel: Rep[scala.math.BigDecimal] =
      column[scala.math.BigDecimal]("block_level", O.Default(scala.math.BigDecimal("-1")))

    /** Database column delegate_value SqlType(varchar), Default(None) */
    val delegateValue: Rep[Option[String]] = column[Option[String]]("delegate_value", O.Default(None))

    /** Database column asof SqlType(timestamp) */
    val asof: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("asof")

    /** Database column is_baker SqlType(bool), Default(false) */
    val isBaker: Rep[Boolean] = column[Boolean]("is_baker", O.Default(false))

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Index over (blockId) (database name ix_accounts_history_block_id) */
    val index1 = index("ix_accounts_history_block_id", blockId)
  }

  /** Collection-like TableQuery object for table AccountsHistory */
  lazy val AccountsHistory = new TableQuery(tag => new AccountsHistory(tag))

  /** Entity class storing rows of table BakingRights
    *  @param blockHash Database column block_hash SqlType(varchar), Default(None)
    *  @param level Database column level SqlType(int4)
    *  @param delegate Database column delegate SqlType(varchar)
    *  @param priority Database column priority SqlType(int4)
    *  @param estimatedTime Database column estimated_time SqlType(timestamp), Default(None)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param governancePeriod Database column governance_period SqlType(int4), Default(None) */
  case class BakingRightsRow(
      blockHash: Option[String] = None,
      level: Int,
      delegate: String,
      priority: Int,
      estimatedTime: Option[java.sql.Timestamp] = None,
      cycle: Option[Int] = None,
      governancePeriod: Option[Int] = None
  )

  /** GetResult implicit for fetching BakingRightsRow objects using plain SQL queries */
  implicit def GetResultBakingRightsRow(
      implicit e0: GR[Option[String]],
      e1: GR[Int],
      e2: GR[String],
      e3: GR[Option[java.sql.Timestamp]],
      e4: GR[Option[Int]]
  ): GR[BakingRightsRow] = GR { prs =>
    import prs._
    BakingRightsRow.tupled((<<?[String], <<[Int], <<[String], <<[Int], <<?[java.sql.Timestamp], <<?[Int], <<?[Int]))
  }

  /** Table description of table baking_rights. Objects of this class serve as prototypes for rows in queries. */
  class BakingRights(_tableTag: Tag)
      extends profile.api.Table[BakingRightsRow](_tableTag, Some("tezos"), "baking_rights") {
    def * =
      (blockHash, level, delegate, priority, estimatedTime, cycle, governancePeriod) <> (BakingRightsRow.tupled, BakingRightsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((blockHash, Rep.Some(level), Rep.Some(delegate), Rep.Some(priority), estimatedTime, cycle, governancePeriod)).shaped
        .<>(
          { r =>
            import r._; _2.map(_ => BakingRightsRow.tupled((_1, _2.get, _3.get, _4.get, _5, _6, _7)))
          },
          (_: Any) => throw new Exception("Inserting into ? projection not supported.")
        )

    /** Database column block_hash SqlType(varchar), Default(None) */
    val blockHash: Rep[Option[String]] = column[Option[String]]("block_hash", O.Default(None))

    /** Database column level SqlType(int4) */
    val level: Rep[Int] = column[Int]("level")

    /** Database column delegate SqlType(varchar) */
    val delegate: Rep[String] = column[String]("delegate")

    /** Database column priority SqlType(int4) */
    val priority: Rep[Int] = column[Int]("priority")

    /** Database column estimated_time SqlType(timestamp), Default(None) */
    val estimatedTime: Rep[Option[java.sql.Timestamp]] =
      column[Option[java.sql.Timestamp]]("estimated_time", O.Default(None))

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Database column governance_period SqlType(int4), Default(None) */
    val governancePeriod: Rep[Option[Int]] = column[Option[Int]]("governance_period", O.Default(None))

    /** Primary key of BakingRights (database name baking_rights_pkey) */
    val pk = primaryKey("baking_rights_pkey", (level, delegate))

    /** Foreign key referencing Blocks (database name fk_block_hash) */
    lazy val blocksFk = foreignKey("fk_block_hash", blockHash, Blocks)(
      r => Rep.Some(r.hash),
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (level) (database name baking_rights_level_idx) */
    val index1 = index("baking_rights_level_idx", level)
  }

  /** Collection-like TableQuery object for table BakingRights */
  lazy val BakingRights = new TableQuery(tag => new BakingRights(tag))

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
  class BalanceUpdates(_tableTag: Tag)
      extends profile.api.Table[BalanceUpdatesRow](_tableTag, Some("tezos"), "balance_updates") {
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

    /** Index over (operationGroupHash) (database name ix_balance_updates_op_group_hash) */
    val index1 = index("ix_balance_updates_op_group_hash", operationGroupHash)
  }

  /** Collection-like TableQuery object for table BalanceUpdates */
  lazy val BalanceUpdates = new TableQuery(tag => new BalanceUpdates(tag))

  /** Entity class storing rows of table BigMapContents
    *  @param bigMapId Database column big_map_id SqlType(numeric)
    *  @param key Database column key SqlType(varchar)
    *  @param keyHash Database column key_hash SqlType(varchar), Default(None)
    *  @param value Database column value SqlType(varchar), Default(None) */
  case class BigMapContentsRow(
      bigMapId: scala.math.BigDecimal,
      key: String,
      keyHash: Option[String] = None,
      value: Option[String] = None
  )

  /** GetResult implicit for fetching BigMapContentsRow objects using plain SQL queries */
  implicit def GetResultBigMapContentsRow(
      implicit e0: GR[scala.math.BigDecimal],
      e1: GR[String],
      e2: GR[Option[String]]
  ): GR[BigMapContentsRow] = GR { prs =>
    import prs._
    BigMapContentsRow.tupled((<<[scala.math.BigDecimal], <<[String], <<?[String], <<?[String]))
  }

  /** Table description of table big_map_contents. Objects of this class serve as prototypes for rows in queries. */
  class BigMapContents(_tableTag: Tag)
      extends profile.api.Table[BigMapContentsRow](_tableTag, Some("tezos"), "big_map_contents") {
    def * = (bigMapId, key, keyHash, value) <> (BigMapContentsRow.tupled, BigMapContentsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(bigMapId), Rep.Some(key), keyHash, value)).shaped.<>({ r =>
        import r._; _1.map(_ => BigMapContentsRow.tupled((_1.get, _2.get, _3, _4)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column big_map_id SqlType(numeric) */
    val bigMapId: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("big_map_id")

    /** Database column key SqlType(varchar) */
    val key: Rep[String] = column[String]("key")

    /** Database column key_hash SqlType(varchar), Default(None) */
    val keyHash: Rep[Option[String]] = column[Option[String]]("key_hash", O.Default(None))

    /** Database column value SqlType(varchar), Default(None) */
    val value: Rep[Option[String]] = column[Option[String]]("value", O.Default(None))

    /** Primary key of BigMapContents (database name big_map_contents_pkey) */
    val pk = primaryKey("big_map_contents_pkey", (bigMapId, key))

    /** Foreign key referencing BigMaps (database name big_map_contents_id_fkey) */
    lazy val bigMapsFk = foreignKey("big_map_contents_id_fkey", bigMapId, BigMaps)(
      r => r.bigMapId,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )
  }

  /** Collection-like TableQuery object for table BigMapContents */
  lazy val BigMapContents = new TableQuery(tag => new BigMapContents(tag))

  /** Entity class storing rows of table BigMaps
    *  @param bigMapId Database column big_map_id SqlType(numeric), PrimaryKey
    *  @param keyType Database column key_type SqlType(varchar), Default(None)
    *  @param valueType Database column value_type SqlType(varchar), Default(None) */
  case class BigMapsRow(
      bigMapId: scala.math.BigDecimal,
      keyType: Option[String] = None,
      valueType: Option[String] = None
  )

  /** GetResult implicit for fetching BigMapsRow objects using plain SQL queries */
  implicit def GetResultBigMapsRow(implicit e0: GR[scala.math.BigDecimal], e1: GR[Option[String]]): GR[BigMapsRow] =
    GR { prs =>
      import prs._
      BigMapsRow.tupled((<<[scala.math.BigDecimal], <<?[String], <<?[String]))
    }

  /** Table description of table big_maps. Objects of this class serve as prototypes for rows in queries. */
  class BigMaps(_tableTag: Tag) extends profile.api.Table[BigMapsRow](_tableTag, Some("tezos"), "big_maps") {
    def * = (bigMapId, keyType, valueType) <> (BigMapsRow.tupled, BigMapsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(bigMapId), keyType, valueType)).shaped.<>({ r =>
        import r._; _1.map(_ => BigMapsRow.tupled((_1.get, _2, _3)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column big_map_id SqlType(numeric), PrimaryKey */
    val bigMapId: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("big_map_id", O.PrimaryKey)

    /** Database column key_type SqlType(varchar), Default(None) */
    val keyType: Rep[Option[String]] = column[Option[String]]("key_type", O.Default(None))

    /** Database column value_type SqlType(varchar), Default(None) */
    val valueType: Rep[Option[String]] = column[Option[String]]("value_type", O.Default(None))
  }

  /** Collection-like TableQuery object for table BigMaps */
  lazy val BigMaps = new TableQuery(tag => new BigMaps(tag))

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
  class Blocks(_tableTag: Tag) extends profile.api.Table[BlocksRow](_tableTag, Some("tezos"), "blocks") {
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
  class Delegates(_tableTag: Tag) extends profile.api.Table[DelegatesRow](_tableTag, Some("tezos"), "delegates") {
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
      extends profile.api.Table[DelegatesCheckpointRow](_tableTag, Some("tezos"), "delegates_checkpoint") {
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

  /** Entity class storing rows of table EndorsingRights
    *  @param blockHash Database column block_hash SqlType(varchar), Default(None)
    *  @param level Database column level SqlType(int4)
    *  @param delegate Database column delegate SqlType(varchar)
    *  @param slot Database column slot SqlType(int4)
    *  @param estimatedTime Database column estimated_time SqlType(timestamp), Default(None)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param governancePeriod Database column governance_period SqlType(int4), Default(None) */
  case class EndorsingRightsRow(
      blockHash: Option[String] = None,
      level: Int,
      delegate: String,
      slot: Int,
      estimatedTime: Option[java.sql.Timestamp] = None,
      cycle: Option[Int] = None,
      governancePeriod: Option[Int] = None
  )

  /** GetResult implicit for fetching EndorsingRightsRow objects using plain SQL queries */
  implicit def GetResultEndorsingRightsRow(
      implicit e0: GR[Option[String]],
      e1: GR[Int],
      e2: GR[String],
      e3: GR[Option[java.sql.Timestamp]],
      e4: GR[Option[Int]]
  ): GR[EndorsingRightsRow] = GR { prs =>
    import prs._
    EndorsingRightsRow.tupled((<<?[String], <<[Int], <<[String], <<[Int], <<?[java.sql.Timestamp], <<?[Int], <<?[Int]))
  }

  /** Table description of table endorsing_rights. Objects of this class serve as prototypes for rows in queries. */
  class EndorsingRights(_tableTag: Tag)
      extends profile.api.Table[EndorsingRightsRow](_tableTag, Some("tezos"), "endorsing_rights") {
    def * =
      (blockHash, level, delegate, slot, estimatedTime, cycle, governancePeriod) <> (EndorsingRightsRow.tupled, EndorsingRightsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((blockHash, Rep.Some(level), Rep.Some(delegate), Rep.Some(slot), estimatedTime, cycle, governancePeriod)).shaped
        .<>(
          { r =>
            import r._; _2.map(_ => EndorsingRightsRow.tupled((_1, _2.get, _3.get, _4.get, _5, _6, _7)))
          },
          (_: Any) => throw new Exception("Inserting into ? projection not supported.")
        )

    /** Database column block_hash SqlType(varchar), Default(None) */
    val blockHash: Rep[Option[String]] = column[Option[String]]("block_hash", O.Default(None))

    /** Database column level SqlType(int4) */
    val level: Rep[Int] = column[Int]("level")

    /** Database column delegate SqlType(varchar) */
    val delegate: Rep[String] = column[String]("delegate")

    /** Database column slot SqlType(int4) */
    val slot: Rep[Int] = column[Int]("slot")

    /** Database column estimated_time SqlType(timestamp), Default(None) */
    val estimatedTime: Rep[Option[java.sql.Timestamp]] =
      column[Option[java.sql.Timestamp]]("estimated_time", O.Default(None))

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Database column governance_period SqlType(int4), Default(None) */
    val governancePeriod: Rep[Option[Int]] = column[Option[Int]]("governance_period", O.Default(None))

    /** Primary key of EndorsingRights (database name endorsing_rights_pkey) */
    val pk = primaryKey("endorsing_rights_pkey", (level, delegate, slot))

    /** Foreign key referencing Blocks (database name fk_block_hash) */
    lazy val blocksFk = foreignKey("fk_block_hash", blockHash, Blocks)(
      r => Rep.Some(r.hash),
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (level) (database name endorsing_rights_level_idx) */
    val index1 = index("endorsing_rights_level_idx", level)
  }

  /** Collection-like TableQuery object for table EndorsingRights */
  lazy val EndorsingRights = new TableQuery(tag => new EndorsingRights(tag))

  /** Entity class storing rows of table Fees
    *  @param low Database column low SqlType(int4)
    *  @param medium Database column medium SqlType(int4)
    *  @param high Database column high SqlType(int4)
    *  @param timestamp Database column timestamp SqlType(timestamp)
    *  @param kind Database column kind SqlType(varchar)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param level Database column level SqlType(int4), Default(None) */
  case class FeesRow(
      low: Int,
      medium: Int,
      high: Int,
      timestamp: java.sql.Timestamp,
      kind: String,
      cycle: Option[Int] = None,
      level: Option[Int] = None
  )

  /** GetResult implicit for fetching FeesRow objects using plain SQL queries */
  implicit def GetResultFeesRow(
      implicit e0: GR[Int],
      e1: GR[java.sql.Timestamp],
      e2: GR[String],
      e3: GR[Option[Int]]
  ): GR[FeesRow] = GR { prs =>
    import prs._
    FeesRow.tupled((<<[Int], <<[Int], <<[Int], <<[java.sql.Timestamp], <<[String], <<?[Int], <<?[Int]))
  }

  /** Table description of table fees. Objects of this class serve as prototypes for rows in queries. */
  class Fees(_tableTag: Tag) extends profile.api.Table[FeesRow](_tableTag, Some("tezos"), "fees") {
    def * = (low, medium, high, timestamp, kind, cycle, level) <> (FeesRow.tupled, FeesRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(low), Rep.Some(medium), Rep.Some(high), Rep.Some(timestamp), Rep.Some(kind), cycle, level)).shaped.<>(
        { r =>
          import r._; _1.map(_ => FeesRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7)))
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

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Database column level SqlType(int4), Default(None) */
    val level: Rep[Option[Int]] = column[Option[Int]]("level", O.Default(None))
  }

  /** Collection-like TableQuery object for table Fees */
  lazy val Fees = new TableQuery(tag => new Fees(tag))

  /** Entity class storing rows of table OperationGroups
    *  @param protocol Database column protocol SqlType(varchar)
    *  @param chainId Database column chain_id SqlType(varchar), Default(None)
    *  @param hash Database column hash SqlType(varchar)
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
  class OperationGroups(_tableTag: Tag)
      extends profile.api.Table[OperationGroupsRow](_tableTag, Some("tezos"), "operation_groups") {
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

    /** Database column hash SqlType(varchar) */
    val hash: Rep[String] = column[String]("hash")

    /** Database column branch SqlType(varchar) */
    val branch: Rep[String] = column[String]("branch")

    /** Database column signature SqlType(varchar), Default(None) */
    val signature: Rep[Option[String]] = column[Option[String]]("signature", O.Default(None))

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Primary key of OperationGroups (database name OperationGroups_pkey) */
    val pk = primaryKey("OperationGroups_pkey", (blockId, hash))

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
    *  @param branch Database column branch SqlType(varchar), Default(None)
    *  @param numberOfSlots Database column number_of_slots SqlType(int4), Default(None)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
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
    *  @param proposal Database column proposal SqlType(varchar), Default(None)
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
    *  @param period Database column period SqlType(int4), Default(None)
    *  @param timestamp Database column timestamp SqlType(timestamp) */
  case class OperationsRow(
      branch: Option[String] = None,
      numberOfSlots: Option[Int] = None,
      cycle: Option[Int] = None,
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
      proposal: Option[String] = None,
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
      period: Option[Int] = None,
      timestamp: java.sql.Timestamp
  )

  /** GetResult implicit for fetching OperationsRow objects using plain SQL queries */
  implicit def GetResultOperationsRow(
      implicit e0: GR[Option[String]],
      e1: GR[Option[Int]],
      e2: GR[Int],
      e3: GR[String],
      e4: GR[Option[scala.math.BigDecimal]],
      e5: GR[Option[Boolean]],
      e6: GR[Boolean],
      e7: GR[java.sql.Timestamp]
  ): GR[OperationsRow] = GR { prs =>
    import prs._
    OperationsRow(
      <<?[String],
      <<?[Int],
      <<?[Int],
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
      <<?[String],
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
      <<?[Int],
      <<[java.sql.Timestamp]
    )
  }

  /** Table description of table operations. Objects of this class serve as prototypes for rows in queries. */
  class Operations(_tableTag: Tag) extends profile.api.Table[OperationsRow](_tableTag, Some("tezos"), "operations") {
    def * =
      (branch :: numberOfSlots :: cycle :: operationId :: operationGroupHash :: kind :: level :: delegate :: slots :: nonce :: pkh :: secret :: source :: fee :: counter :: gasLimit :: storageLimit :: publicKey :: amount :: destination :: parameters :: managerPubkey :: balance :: proposal :: spendable :: delegatable :: script :: storage :: status :: consumedGas :: storageSize :: paidStorageSizeDiff :: originatedContracts :: blockHash :: blockLevel :: ballot :: internal :: period :: timestamp :: HNil)
        .mapTo[OperationsRow]

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (branch :: numberOfSlots :: cycle :: Rep.Some(operationId) :: Rep.Some(operationGroupHash) :: Rep.Some(kind) :: level :: delegate :: slots :: nonce :: pkh :: secret :: source :: fee :: counter :: gasLimit :: storageLimit :: publicKey :: amount :: destination :: parameters :: managerPubkey :: balance :: proposal :: spendable :: delegatable :: script :: storage :: status :: consumedGas :: storageSize :: paidStorageSizeDiff :: originatedContracts :: Rep
            .Some(
              blockHash
            ) :: Rep.Some(blockLevel) :: ballot :: Rep.Some(internal) :: period :: Rep.Some(timestamp) :: HNil).shaped
        .<>(
          r =>
            OperationsRow(
              r(0).asInstanceOf[Option[String]],
              r(1).asInstanceOf[Option[Int]],
              r(2).asInstanceOf[Option[Int]],
              r(3).asInstanceOf[Option[Int]].get,
              r(4).asInstanceOf[Option[String]].get,
              r(5).asInstanceOf[Option[String]].get,
              r(6).asInstanceOf[Option[Int]],
              r(7).asInstanceOf[Option[String]],
              r(8).asInstanceOf[Option[String]],
              r(9).asInstanceOf[Option[String]],
              r(10).asInstanceOf[Option[String]],
              r(11).asInstanceOf[Option[String]],
              r(12).asInstanceOf[Option[String]],
              r(13).asInstanceOf[Option[scala.math.BigDecimal]],
              r(14).asInstanceOf[Option[scala.math.BigDecimal]],
              r(15).asInstanceOf[Option[scala.math.BigDecimal]],
              r(16).asInstanceOf[Option[scala.math.BigDecimal]],
              r(17).asInstanceOf[Option[String]],
              r(18).asInstanceOf[Option[scala.math.BigDecimal]],
              r(19).asInstanceOf[Option[String]],
              r(20).asInstanceOf[Option[String]],
              r(21).asInstanceOf[Option[String]],
              r(22).asInstanceOf[Option[scala.math.BigDecimal]],
              r(23).asInstanceOf[Option[String]],
              r(24).asInstanceOf[Option[Boolean]],
              r(25).asInstanceOf[Option[Boolean]],
              r(26).asInstanceOf[Option[String]],
              r(27).asInstanceOf[Option[String]],
              r(28).asInstanceOf[Option[String]],
              r(29).asInstanceOf[Option[scala.math.BigDecimal]],
              r(30).asInstanceOf[Option[scala.math.BigDecimal]],
              r(31).asInstanceOf[Option[scala.math.BigDecimal]],
              r(32).asInstanceOf[Option[String]],
              r(33).asInstanceOf[Option[String]].get,
              r(34).asInstanceOf[Option[Int]].get,
              r(35).asInstanceOf[Option[String]],
              r(36).asInstanceOf[Option[Boolean]].get,
              r(37).asInstanceOf[Option[Int]],
              r(38).asInstanceOf[Option[java.sql.Timestamp]].get
            ),
          (_: Any) => throw new Exception("Inserting into ? projection not supported.")
        )

    /** Database column branch SqlType(varchar), Default(None) */
    val branch: Rep[Option[String]] = column[Option[String]]("branch", O.Default(None))

    /** Database column number_of_slots SqlType(int4), Default(None) */
    val numberOfSlots: Rep[Option[Int]] = column[Option[Int]]("number_of_slots", O.Default(None))

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

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

    /** Database column proposal SqlType(varchar), Default(None) */
    val proposal: Rep[Option[String]] = column[Option[String]]("proposal", O.Default(None))

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

    /** Database column period SqlType(int4), Default(None) */
    val period: Rep[Option[Int]] = column[Option[Int]]("period", O.Default(None))

    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")

    /** Foreign key referencing Blocks (database name fk_blockhashes) */
    lazy val blocksFk = foreignKey("fk_blockhashes", blockHash :: HNil, Blocks)(
      r => r.hash :: HNil,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Foreign key referencing OperationGroups (database name fk_opgroups) */
    lazy val operationGroupsFk = foreignKey("fk_opgroups", operationGroupHash :: blockHash :: HNil, OperationGroups)(
      r => r.hash :: r.blockId :: HNil,
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

  /** Entity class storing rows of table OriginatedAccountMaps
    *  @param bigMapId Database column big_map_id SqlType(numeric)
    *  @param accountId Database column account_id SqlType(varchar) */
  case class OriginatedAccountMapsRow(bigMapId: scala.math.BigDecimal, accountId: String)

  /** GetResult implicit for fetching OriginatedAccountMapsRow objects using plain SQL queries */
  implicit def GetResultOriginatedAccountMapsRow(
      implicit e0: GR[scala.math.BigDecimal],
      e1: GR[String]
  ): GR[OriginatedAccountMapsRow] = GR { prs =>
    import prs._
    OriginatedAccountMapsRow.tupled((<<[scala.math.BigDecimal], <<[String]))
  }

  /** Table description of table originated_account_maps. Objects of this class serve as prototypes for rows in queries. */
  class OriginatedAccountMaps(_tableTag: Tag)
      extends profile.api.Table[OriginatedAccountMapsRow](_tableTag, Some("tezos"), "originated_account_maps") {
    def * = (bigMapId, accountId) <> (OriginatedAccountMapsRow.tupled, OriginatedAccountMapsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(bigMapId), Rep.Some(accountId))).shaped.<>({ r =>
        import r._; _1.map(_ => OriginatedAccountMapsRow.tupled((_1.get, _2.get)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column big_map_id SqlType(numeric) */
    val bigMapId: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("big_map_id")

    /** Database column account_id SqlType(varchar) */
    val accountId: Rep[String] = column[String]("account_id")

    /** Primary key of OriginatedAccountMaps (database name originated_account_maps_pkey) */
    val pk = primaryKey("originated_account_maps_pkey", (bigMapId, accountId))

    /** Index over (accountId) (database name accounts_maps_idx) */
    val index1 = index("accounts_maps_idx", accountId)
  }

  /** Collection-like TableQuery object for table OriginatedAccountMaps */
  lazy val OriginatedAccountMaps = new TableQuery(tag => new OriginatedAccountMaps(tag))

  /** Entity class storing rows of table ProcessedChainEvents
    *  @param eventLevel Database column event_level SqlType(numeric)
    *  @param eventType Database column event_type SqlType(varchar) */
  case class ProcessedChainEventsRow(eventLevel: scala.math.BigDecimal, eventType: String)

  /** GetResult implicit for fetching ProcessedChainEventsRow objects using plain SQL queries */
  implicit def GetResultProcessedChainEventsRow(
      implicit e0: GR[scala.math.BigDecimal],
      e1: GR[String]
  ): GR[ProcessedChainEventsRow] = GR { prs =>
    import prs._
    ProcessedChainEventsRow.tupled((<<[scala.math.BigDecimal], <<[String]))
  }

  /** Table description of table processed_chain_events. Objects of this class serve as prototypes for rows in queries. */
  class ProcessedChainEvents(_tableTag: Tag)
      extends profile.api.Table[ProcessedChainEventsRow](_tableTag, Some("tezos"), "processed_chain_events") {
    def * = (eventLevel, eventType) <> (ProcessedChainEventsRow.tupled, ProcessedChainEventsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(eventLevel), Rep.Some(eventType))).shaped.<>({ r =>
        import r._; _1.map(_ => ProcessedChainEventsRow.tupled((_1.get, _2.get)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column event_level SqlType(numeric) */
    val eventLevel: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("event_level")

    /** Database column event_type SqlType(varchar) */
    val eventType: Rep[String] = column[String]("event_type")

    /** Primary key of ProcessedChainEvents (database name processed_chain_events_pkey) */
    val pk = primaryKey("processed_chain_events_pkey", (eventLevel, eventType))
  }

  /** Collection-like TableQuery object for table ProcessedChainEvents */
  lazy val ProcessedChainEvents = new TableQuery(tag => new ProcessedChainEvents(tag))

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
  class Rolls(_tableTag: Tag) extends profile.api.Table[RollsRow](_tableTag, Some("tezos"), "rolls") {
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
