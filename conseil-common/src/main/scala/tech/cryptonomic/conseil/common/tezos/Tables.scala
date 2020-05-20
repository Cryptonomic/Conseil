package tech.cryptonomic.conseil.common.tezos
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
    BakerRegistry.schema,
    Bakers.schema,
    BakersCheckpoint.schema,
    BakersHistory.schema,
    BakingRights.schema,
    BalanceUpdates.schema,
    BigMapContents.schema,
    BigMaps.schema,
    Blocks.schema,
    EndorsingRights.schema,
    Fees.schema,
    Governance.schema,
    KnownAddresses.schema,
    OperationGroups.schema,
    Operations.schema,
    OriginatedAccountMaps.schema,
    ProcessedChainEvents.schema,
    RegisteredTokens.schema,
    TezosNames.schema,
    TokenBalances.schema
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
    *  @param isBaker Database column is_baker SqlType(bool), Default(false)
    *  @param isActivated Database column is_activated SqlType(bool), Default(false) */
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
      isBaker: Boolean = false,
      isActivated: Boolean = false
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
        <<[Boolean],
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
        isBaker,
        isActivated
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
          Rep.Some(isBaker),
          Rep.Some(isActivated)
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(
            _ => AccountsRow.tupled((_1.get, _2.get, _3, _4, _5, _6.get, _7.get, _8, _9, _10, _11, _12.get, _13.get))
          )
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

    /** Database column is_activated SqlType(bool), Default(false) */
    val isActivated: Rep[Boolean] = column[Boolean]("is_activated", O.Default(false))

    /** Foreign key referencing Blocks (database name accounts_block_id_fkey) */
    lazy val blocksFk = foreignKey("accounts_block_id_fkey", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (blockLevel) (database name ix_accounts_block_level) */
    val index1 = index("ix_accounts_block_level", blockLevel)

    /** Index over (isActivated) (database name ix_accounts_is_activated) */
    val index2 = index("ix_accounts_is_activated", isActivated)

    /** Index over (manager) (database name ix_accounts_manager) */
    val index3 = index("ix_accounts_manager", manager)
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
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param isActivated Database column is_activated SqlType(bool), Default(false)
    *  @param isActiveBaker Database column is_active_baker SqlType(bool), Default(None) */
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
      cycle: Option[Int] = None,
      isActivated: Boolean = false,
      isActiveBaker: Option[Boolean] = None
  )

  /** GetResult implicit for fetching AccountsHistoryRow objects using plain SQL queries */
  implicit def GetResultAccountsHistoryRow(
      implicit e0: GR[String],
      e1: GR[Option[Int]],
      e2: GR[Option[String]],
      e3: GR[scala.math.BigDecimal],
      e4: GR[java.sql.Timestamp],
      e5: GR[Boolean],
      e6: GR[Option[Boolean]]
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
        <<?[Int],
        <<[Boolean],
        <<?[Boolean]
      )
    )
  }

  /** Table description of table accounts_history. Objects of this class serve as prototypes for rows in queries. */
  class AccountsHistory(_tableTag: Tag)
      extends profile.api.Table[AccountsHistoryRow](_tableTag, Some("tezos"), "accounts_history") {
    def * =
      (
        accountId,
        blockId,
        counter,
        storage,
        balance,
        blockLevel,
        delegateValue,
        asof,
        isBaker,
        cycle,
        isActivated,
        isActiveBaker
      ) <> (AccountsHistoryRow.tupled, AccountsHistoryRow.unapply)

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
          cycle,
          Rep.Some(isActivated),
          isActiveBaker
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(
            _ =>
              AccountsHistoryRow.tupled((_1.get, _2.get, _3, _4, _5.get, _6.get, _7, _8.get, _9.get, _10, _11.get, _12))
          )
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

    /** Database column is_activated SqlType(bool), Default(false) */
    val isActivated: Rep[Boolean] = column[Boolean]("is_activated", O.Default(false))

    /** Database column is_active_baker SqlType(bool), Default(None) */
    val isActiveBaker: Rep[Option[Boolean]] = column[Option[Boolean]]("is_active_baker", O.Default(None))

    /** Index over (blockId) (database name ix_accounts_history_block_id) */
    val index1 = index("ix_accounts_history_block_id", blockId)
  }

  /** Collection-like TableQuery object for table AccountsHistory */
  lazy val AccountsHistory = new TableQuery(tag => new AccountsHistory(tag))

  /** Entity class storing rows of table BakerRegistry
    *  @param name Database column name SqlType(varchar)
    *  @param isAcceptingDelegation Database column is_accepting_delegation SqlType(bool), Default(None)
    *  @param externalDataUrl Database column external_data_url SqlType(varchar), Default(None)
    *  @param split Database column split SqlType(numeric), Default(None)
    *  @param paymentAccounts Database column payment_accounts SqlType(varchar), Default(None)
    *  @param minimumDelegation Database column minimum_delegation SqlType(int4), Default(None)
    *  @param payoutDelay Database column payout_delay SqlType(int4), Default(None)
    *  @param payoutFrequency Database column payout_frequency SqlType(int4), Default(None)
    *  @param minimumPayout Database column minimum_payout SqlType(int4), Default(None)
    *  @param isCheap Database column is_cheap SqlType(bool), Default(None)
    *  @param payForOwnBlocks Database column pay_for_own_blocks SqlType(bool), Default(None)
    *  @param payForEndorsements Database column pay_for_endorsements SqlType(bool), Default(None)
    *  @param payGainedFees Database column pay_gained_fees SqlType(bool), Default(None)
    *  @param payForAccusationGains Database column pay_for_accusation_gains SqlType(bool), Default(None)
    *  @param subtractLostDepositsWhenAccused Database column subtract_lost_deposits_when_accused SqlType(bool), Default(None)
    *  @param subtractLostRewardsWhenAccused Database column subtract_lost_rewards_when_accused SqlType(bool), Default(None)
    *  @param subtractLostFeesWhenAccused Database column subtract_lost_fees_when_accused SqlType(bool), Default(None)
    *  @param payForRevelation Database column pay_for_revelation SqlType(bool), Default(None)
    *  @param subtractLostRewardsWhenMissRevelation Database column subtract_lost_rewards_when_miss_revelation SqlType(bool), Default(None)
    *  @param subtractLostFeesWhenMissRevelation Database column subtract_lost_fees_when_miss_revelation SqlType(bool), Default(None)
    *  @param compensateMissedBlocks Database column compensate_missed_blocks SqlType(bool), Default(None)
    *  @param payForStolenBlocks Database column pay_for_stolen_blocks SqlType(bool), Default(None)
    *  @param compensateMissedEndorsements Database column compensate_missed_endorsements SqlType(bool), Default(None)
    *  @param compensateLowPriorityEndorsementLoss Database column compensate_low_priority_endorsement_loss SqlType(bool), Default(None)
    *  @param overdelegationThreshold Database column overdelegation_threshold SqlType(int4), Default(None)
    *  @param subtractRewardsFromUninvitedDelegation Database column subtract_rewards_from_uninvited_delegation SqlType(bool), Default(None)
    *  @param recordManager Database column record_manager SqlType(varchar), Default(None)
    *  @param timestamp Database column timestamp SqlType(timestamp) */
  case class BakerRegistryRow(
      name: String,
      isAcceptingDelegation: Option[Boolean] = None,
      externalDataUrl: Option[String] = None,
      split: Option[scala.math.BigDecimal] = None,
      paymentAccounts: Option[String] = None,
      minimumDelegation: Option[Int] = None,
      payoutDelay: Option[Int] = None,
      payoutFrequency: Option[Int] = None,
      minimumPayout: Option[Int] = None,
      isCheap: Option[Boolean] = None,
      payForOwnBlocks: Option[Boolean] = None,
      payForEndorsements: Option[Boolean] = None,
      payGainedFees: Option[Boolean] = None,
      payForAccusationGains: Option[Boolean] = None,
      subtractLostDepositsWhenAccused: Option[Boolean] = None,
      subtractLostRewardsWhenAccused: Option[Boolean] = None,
      subtractLostFeesWhenAccused: Option[Boolean] = None,
      payForRevelation: Option[Boolean] = None,
      subtractLostRewardsWhenMissRevelation: Option[Boolean] = None,
      subtractLostFeesWhenMissRevelation: Option[Boolean] = None,
      compensateMissedBlocks: Option[Boolean] = None,
      payForStolenBlocks: Option[Boolean] = None,
      compensateMissedEndorsements: Option[Boolean] = None,
      compensateLowPriorityEndorsementLoss: Option[Boolean] = None,
      overdelegationThreshold: Option[Int] = None,
      subtractRewardsFromUninvitedDelegation: Option[Boolean] = None,
      recordManager: Option[String] = None,
      timestamp: java.sql.Timestamp
  )

  /** GetResult implicit for fetching BakerRegistryRow objects using plain SQL queries */
  implicit def GetResultBakerRegistryRow(
      implicit e0: GR[String],
      e1: GR[Option[Boolean]],
      e2: GR[Option[String]],
      e3: GR[Option[scala.math.BigDecimal]],
      e4: GR[Option[Int]],
      e5: GR[java.sql.Timestamp]
  ): GR[BakerRegistryRow] = GR { prs =>
    import prs._
    BakerRegistryRow(
      <<[String],
      <<?[Boolean],
      <<?[String],
      <<?[scala.math.BigDecimal],
      <<?[String],
      <<?[Int],
      <<?[Int],
      <<?[Int],
      <<?[Int],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Boolean],
      <<?[Int],
      <<?[Boolean],
      <<?[String],
      <<[java.sql.Timestamp]
    )
  }

  /** Table description of table baker_registry. Objects of this class serve as prototypes for rows in queries. */
  class BakerRegistry(_tableTag: Tag)
      extends profile.api.Table[BakerRegistryRow](_tableTag, Some("tezos"), "baker_registry") {
    def * =
      (name :: isAcceptingDelegation :: externalDataUrl :: split :: paymentAccounts :: minimumDelegation :: payoutDelay :: payoutFrequency :: minimumPayout :: isCheap :: payForOwnBlocks :: payForEndorsements :: payGainedFees :: payForAccusationGains :: subtractLostDepositsWhenAccused :: subtractLostRewardsWhenAccused :: subtractLostFeesWhenAccused :: payForRevelation :: subtractLostRewardsWhenMissRevelation :: subtractLostFeesWhenMissRevelation :: compensateMissedBlocks :: payForStolenBlocks :: compensateMissedEndorsements :: compensateLowPriorityEndorsementLoss :: overdelegationThreshold :: subtractRewardsFromUninvitedDelegation :: recordManager :: timestamp :: HNil)
        .mapTo[BakerRegistryRow]

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (Rep.Some(name) :: isAcceptingDelegation :: externalDataUrl :: split :: paymentAccounts :: minimumDelegation :: payoutDelay :: payoutFrequency :: minimumPayout :: isCheap :: payForOwnBlocks :: payForEndorsements :: payGainedFees :: payForAccusationGains :: subtractLostDepositsWhenAccused :: subtractLostRewardsWhenAccused :: subtractLostFeesWhenAccused :: payForRevelation :: subtractLostRewardsWhenMissRevelation :: subtractLostFeesWhenMissRevelation :: compensateMissedBlocks :: payForStolenBlocks :: compensateMissedEndorsements :: compensateLowPriorityEndorsementLoss :: overdelegationThreshold :: subtractRewardsFromUninvitedDelegation :: recordManager :: Rep
            .Some(timestamp) :: HNil).shaped.<>(
        r =>
          BakerRegistryRow(
            r(0).asInstanceOf[Option[String]].get,
            r(1).asInstanceOf[Option[Boolean]],
            r(2).asInstanceOf[Option[String]],
            r(3).asInstanceOf[Option[scala.math.BigDecimal]],
            r(4).asInstanceOf[Option[String]],
            r(5).asInstanceOf[Option[Int]],
            r(6).asInstanceOf[Option[Int]],
            r(7).asInstanceOf[Option[Int]],
            r(8).asInstanceOf[Option[Int]],
            r(9).asInstanceOf[Option[Boolean]],
            r(10).asInstanceOf[Option[Boolean]],
            r(11).asInstanceOf[Option[Boolean]],
            r(12).asInstanceOf[Option[Boolean]],
            r(13).asInstanceOf[Option[Boolean]],
            r(14).asInstanceOf[Option[Boolean]],
            r(15).asInstanceOf[Option[Boolean]],
            r(16).asInstanceOf[Option[Boolean]],
            r(17).asInstanceOf[Option[Boolean]],
            r(18).asInstanceOf[Option[Boolean]],
            r(19).asInstanceOf[Option[Boolean]],
            r(20).asInstanceOf[Option[Boolean]],
            r(21).asInstanceOf[Option[Boolean]],
            r(22).asInstanceOf[Option[Boolean]],
            r(23).asInstanceOf[Option[Boolean]],
            r(24).asInstanceOf[Option[Int]],
            r(25).asInstanceOf[Option[Boolean]],
            r(26).asInstanceOf[Option[String]],
            r(27).asInstanceOf[Option[java.sql.Timestamp]].get
          ),
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column name SqlType(varchar) */
    val name: Rep[String] = column[String]("name")

    /** Database column is_accepting_delegation SqlType(bool), Default(None) */
    val isAcceptingDelegation: Rep[Option[Boolean]] =
      column[Option[Boolean]]("is_accepting_delegation", O.Default(None))

    /** Database column external_data_url SqlType(varchar), Default(None) */
    val externalDataUrl: Rep[Option[String]] = column[Option[String]]("external_data_url", O.Default(None))

    /** Database column split SqlType(numeric), Default(None) */
    val split: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("split", O.Default(None))

    /** Database column payment_accounts SqlType(varchar), Default(None) */
    val paymentAccounts: Rep[Option[String]] = column[Option[String]]("payment_accounts", O.Default(None))

    /** Database column minimum_delegation SqlType(int4), Default(None) */
    val minimumDelegation: Rep[Option[Int]] = column[Option[Int]]("minimum_delegation", O.Default(None))

    /** Database column payout_delay SqlType(int4), Default(None) */
    val payoutDelay: Rep[Option[Int]] = column[Option[Int]]("payout_delay", O.Default(None))

    /** Database column payout_frequency SqlType(int4), Default(None) */
    val payoutFrequency: Rep[Option[Int]] = column[Option[Int]]("payout_frequency", O.Default(None))

    /** Database column minimum_payout SqlType(int4), Default(None) */
    val minimumPayout: Rep[Option[Int]] = column[Option[Int]]("minimum_payout", O.Default(None))

    /** Database column is_cheap SqlType(bool), Default(None) */
    val isCheap: Rep[Option[Boolean]] = column[Option[Boolean]]("is_cheap", O.Default(None))

    /** Database column pay_for_own_blocks SqlType(bool), Default(None) */
    val payForOwnBlocks: Rep[Option[Boolean]] = column[Option[Boolean]]("pay_for_own_blocks", O.Default(None))

    /** Database column pay_for_endorsements SqlType(bool), Default(None) */
    val payForEndorsements: Rep[Option[Boolean]] = column[Option[Boolean]]("pay_for_endorsements", O.Default(None))

    /** Database column pay_gained_fees SqlType(bool), Default(None) */
    val payGainedFees: Rep[Option[Boolean]] = column[Option[Boolean]]("pay_gained_fees", O.Default(None))

    /** Database column pay_for_accusation_gains SqlType(bool), Default(None) */
    val payForAccusationGains: Rep[Option[Boolean]] =
      column[Option[Boolean]]("pay_for_accusation_gains", O.Default(None))

    /** Database column subtract_lost_deposits_when_accused SqlType(bool), Default(None) */
    val subtractLostDepositsWhenAccused: Rep[Option[Boolean]] =
      column[Option[Boolean]]("subtract_lost_deposits_when_accused", O.Default(None))

    /** Database column subtract_lost_rewards_when_accused SqlType(bool), Default(None) */
    val subtractLostRewardsWhenAccused: Rep[Option[Boolean]] =
      column[Option[Boolean]]("subtract_lost_rewards_when_accused", O.Default(None))

    /** Database column subtract_lost_fees_when_accused SqlType(bool), Default(None) */
    val subtractLostFeesWhenAccused: Rep[Option[Boolean]] =
      column[Option[Boolean]]("subtract_lost_fees_when_accused", O.Default(None))

    /** Database column pay_for_revelation SqlType(bool), Default(None) */
    val payForRevelation: Rep[Option[Boolean]] = column[Option[Boolean]]("pay_for_revelation", O.Default(None))

    /** Database column subtract_lost_rewards_when_miss_revelation SqlType(bool), Default(None) */
    val subtractLostRewardsWhenMissRevelation: Rep[Option[Boolean]] =
      column[Option[Boolean]]("subtract_lost_rewards_when_miss_revelation", O.Default(None))

    /** Database column subtract_lost_fees_when_miss_revelation SqlType(bool), Default(None) */
    val subtractLostFeesWhenMissRevelation: Rep[Option[Boolean]] =
      column[Option[Boolean]]("subtract_lost_fees_when_miss_revelation", O.Default(None))

    /** Database column compensate_missed_blocks SqlType(bool), Default(None) */
    val compensateMissedBlocks: Rep[Option[Boolean]] =
      column[Option[Boolean]]("compensate_missed_blocks", O.Default(None))

    /** Database column pay_for_stolen_blocks SqlType(bool), Default(None) */
    val payForStolenBlocks: Rep[Option[Boolean]] = column[Option[Boolean]]("pay_for_stolen_blocks", O.Default(None))

    /** Database column compensate_missed_endorsements SqlType(bool), Default(None) */
    val compensateMissedEndorsements: Rep[Option[Boolean]] =
      column[Option[Boolean]]("compensate_missed_endorsements", O.Default(None))

    /** Database column compensate_low_priority_endorsement_loss SqlType(bool), Default(None) */
    val compensateLowPriorityEndorsementLoss: Rep[Option[Boolean]] =
      column[Option[Boolean]]("compensate_low_priority_endorsement_loss", O.Default(None))

    /** Database column overdelegation_threshold SqlType(int4), Default(None) */
    val overdelegationThreshold: Rep[Option[Int]] = column[Option[Int]]("overdelegation_threshold", O.Default(None))

    /** Database column subtract_rewards_from_uninvited_delegation SqlType(bool), Default(None) */
    val subtractRewardsFromUninvitedDelegation: Rep[Option[Boolean]] =
      column[Option[Boolean]]("subtract_rewards_from_uninvited_delegation", O.Default(None))

    /** Database column record_manager SqlType(varchar), Default(None) */
    val recordManager: Rep[Option[String]] = column[Option[String]]("record_manager", O.Default(None))

    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")
  }

  /** Collection-like TableQuery object for table BakerRegistry */
  lazy val BakerRegistry = new TableQuery(tag => new BakerRegistry(tag))

  /** Entity class storing rows of table Bakers
    *  @param pkh Database column pkh SqlType(varchar), PrimaryKey
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param balance Database column balance SqlType(numeric), Default(None)
    *  @param frozenBalance Database column frozen_balance SqlType(numeric), Default(None)
    *  @param stakingBalance Database column staking_balance SqlType(numeric), Default(None)
    *  @param delegatedBalance Database column delegated_balance SqlType(numeric), Default(None)
    *  @param rolls Database column rolls SqlType(int4), Default(0)
    *  @param deactivated Database column deactivated SqlType(bool)
    *  @param gracePeriod Database column grace_period SqlType(int4)
    *  @param blockLevel Database column block_level SqlType(int4), Default(-1)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param period Database column period SqlType(int4), Default(None) */
  case class BakersRow(
      pkh: String,
      blockId: String,
      balance: Option[scala.math.BigDecimal] = None,
      frozenBalance: Option[scala.math.BigDecimal] = None,
      stakingBalance: Option[scala.math.BigDecimal] = None,
      delegatedBalance: Option[scala.math.BigDecimal] = None,
      rolls: Int = 0,
      deactivated: Boolean,
      gracePeriod: Int,
      blockLevel: Int = -1,
      cycle: Option[Int] = None,
      period: Option[Int] = None
  )

  /** GetResult implicit for fetching BakersRow objects using plain SQL queries */
  implicit def GetResultBakersRow(
      implicit e0: GR[String],
      e1: GR[Option[scala.math.BigDecimal]],
      e2: GR[Int],
      e3: GR[Boolean],
      e4: GR[Option[Int]]
  ): GR[BakersRow] = GR { prs =>
    import prs._
    BakersRow.tupled(
      (
        <<[String],
        <<[String],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<[Int],
        <<[Boolean],
        <<[Int],
        <<[Int],
        <<?[Int],
        <<?[Int]
      )
    )
  }

  /** Table description of table bakers. Objects of this class serve as prototypes for rows in queries. */
  class Bakers(_tableTag: Tag) extends profile.api.Table[BakersRow](_tableTag, Some("tezos"), "bakers") {
    def * =
      (
        pkh,
        blockId,
        balance,
        frozenBalance,
        stakingBalance,
        delegatedBalance,
        rolls,
        deactivated,
        gracePeriod,
        blockLevel,
        cycle,
        period
      ) <> (BakersRow.tupled, BakersRow.unapply)

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
          Rep.Some(rolls),
          Rep.Some(deactivated),
          Rep.Some(gracePeriod),
          Rep.Some(blockLevel),
          cycle,
          period
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ => BakersRow.tupled((_1.get, _2.get, _3, _4, _5, _6, _7.get, _8.get, _9.get, _10.get, _11, _12)))
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

    /** Database column rolls SqlType(int4), Default(0) */
    val rolls: Rep[Int] = column[Int]("rolls", O.Default(0))

    /** Database column deactivated SqlType(bool) */
    val deactivated: Rep[Boolean] = column[Boolean]("deactivated")

    /** Database column grace_period SqlType(int4) */
    val gracePeriod: Rep[Int] = column[Int]("grace_period")

    /** Database column block_level SqlType(int4), Default(-1) */
    val blockLevel: Rep[Int] = column[Int]("block_level", O.Default(-1))

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Database column period SqlType(int4), Default(None) */
    val period: Rep[Option[Int]] = column[Option[Int]]("period", O.Default(None))

    /** Foreign key referencing Blocks (database name bakers_block_id_fkey) */
    lazy val blocksFk = foreignKey("bakers_block_id_fkey", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )
  }

  /** Collection-like TableQuery object for table Bakers */
  lazy val Bakers = new TableQuery(tag => new Bakers(tag))

  /** Entity class storing rows of table BakersCheckpoint
    *  @param delegatePkh Database column delegate_pkh SqlType(varchar)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param blockLevel Database column block_level SqlType(int4), Default(-1)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param period Database column period SqlType(int4), Default(None) */
  case class BakersCheckpointRow(
      delegatePkh: String,
      blockId: String,
      blockLevel: Int = -1,
      cycle: Option[Int] = None,
      period: Option[Int] = None
  )

  /** GetResult implicit for fetching BakersCheckpointRow objects using plain SQL queries */
  implicit def GetResultBakersCheckpointRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[Option[Int]]
  ): GR[BakersCheckpointRow] = GR { prs =>
    import prs._
    BakersCheckpointRow.tupled((<<[String], <<[String], <<[Int], <<?[Int], <<?[Int]))
  }

  /** Table description of table bakers_checkpoint. Objects of this class serve as prototypes for rows in queries. */
  class BakersCheckpoint(_tableTag: Tag)
      extends profile.api.Table[BakersCheckpointRow](_tableTag, Some("tezos"), "bakers_checkpoint") {
    def * =
      (delegatePkh, blockId, blockLevel, cycle, period) <> (BakersCheckpointRow.tupled, BakersCheckpointRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(delegatePkh), Rep.Some(blockId), Rep.Some(blockLevel), cycle, period)).shaped.<>(
        { r =>
          import r._; _1.map(_ => BakersCheckpointRow.tupled((_1.get, _2.get, _3.get, _4, _5)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column delegate_pkh SqlType(varchar) */
    val delegatePkh: Rep[String] = column[String]("delegate_pkh")

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column block_level SqlType(int4), Default(-1) */
    val blockLevel: Rep[Int] = column[Int]("block_level", O.Default(-1))

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Database column period SqlType(int4), Default(None) */
    val period: Rep[Option[Int]] = column[Option[Int]]("period", O.Default(None))

    /** Foreign key referencing Blocks (database name baker_checkpoint_block_id_fkey) */
    lazy val blocksFk = foreignKey("baker_checkpoint_block_id_fkey", blockId, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (blockLevel) (database name ix_bakers_checkpoint_block_level) */
    val index1 = index("ix_bakers_checkpoint_block_level", blockLevel)
  }

  /** Collection-like TableQuery object for table BakersCheckpoint */
  lazy val BakersCheckpoint = new TableQuery(tag => new BakersCheckpoint(tag))

  /** Entity class storing rows of table BakersHistory
    *  @param pkh Database column pkh SqlType(varchar)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param balance Database column balance SqlType(numeric), Default(None)
    *  @param frozenBalance Database column frozen_balance SqlType(numeric), Default(None)
    *  @param stakingBalance Database column staking_balance SqlType(numeric), Default(None)
    *  @param delegatedBalance Database column delegated_balance SqlType(numeric), Default(None)
    *  @param rolls Database column rolls SqlType(int4), Default(0)
    *  @param deactivated Database column deactivated SqlType(bool)
    *  @param gracePeriod Database column grace_period SqlType(int4)
    *  @param blockLevel Database column block_level SqlType(int4), Default(-1)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param period Database column period SqlType(int4), Default(None)
    *  @param asof Database column asof SqlType(timestamp) */
  case class BakersHistoryRow(
      pkh: String,
      blockId: String,
      balance: Option[scala.math.BigDecimal] = None,
      frozenBalance: Option[scala.math.BigDecimal] = None,
      stakingBalance: Option[scala.math.BigDecimal] = None,
      delegatedBalance: Option[scala.math.BigDecimal] = None,
      rolls: Int = 0,
      deactivated: Boolean,
      gracePeriod: Int,
      blockLevel: Int = -1,
      cycle: Option[Int] = None,
      period: Option[Int] = None,
      asof: java.sql.Timestamp
  )

  /** GetResult implicit for fetching BakersHistoryRow objects using plain SQL queries */
  implicit def GetResultBakersHistoryRow(
      implicit e0: GR[String],
      e1: GR[Option[scala.math.BigDecimal]],
      e2: GR[Int],
      e3: GR[Boolean],
      e4: GR[Option[Int]],
      e5: GR[java.sql.Timestamp]
  ): GR[BakersHistoryRow] = GR { prs =>
    import prs._
    BakersHistoryRow.tupled(
      (
        <<[String],
        <<[String],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<[Int],
        <<[Boolean],
        <<[Int],
        <<[Int],
        <<?[Int],
        <<?[Int],
        <<[java.sql.Timestamp]
      )
    )
  }

  /** Table description of table bakers_history. Objects of this class serve as prototypes for rows in queries. */
  class BakersHistory(_tableTag: Tag)
      extends profile.api.Table[BakersHistoryRow](_tableTag, Some("tezos"), "bakers_history") {
    def * =
      (
        pkh,
        blockId,
        balance,
        frozenBalance,
        stakingBalance,
        delegatedBalance,
        rolls,
        deactivated,
        gracePeriod,
        blockLevel,
        cycle,
        period,
        asof
      ) <> (BakersHistoryRow.tupled, BakersHistoryRow.unapply)

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
          Rep.Some(rolls),
          Rep.Some(deactivated),
          Rep.Some(gracePeriod),
          Rep.Some(blockLevel),
          cycle,
          period,
          Rep.Some(asof)
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(
            _ =>
              BakersHistoryRow
                .tupled((_1.get, _2.get, _3, _4, _5, _6, _7.get, _8.get, _9.get, _10.get, _11, _12, _13.get))
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column pkh SqlType(varchar) */
    val pkh: Rep[String] = column[String]("pkh")

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

    /** Database column rolls SqlType(int4), Default(0) */
    val rolls: Rep[Int] = column[Int]("rolls", O.Default(0))

    /** Database column deactivated SqlType(bool) */
    val deactivated: Rep[Boolean] = column[Boolean]("deactivated")

    /** Database column grace_period SqlType(int4) */
    val gracePeriod: Rep[Int] = column[Int]("grace_period")

    /** Database column block_level SqlType(int4), Default(-1) */
    val blockLevel: Rep[Int] = column[Int]("block_level", O.Default(-1))

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Database column period SqlType(int4), Default(None) */
    val period: Rep[Option[Int]] = column[Option[Int]]("period", O.Default(None))

    /** Database column asof SqlType(timestamp) */
    val asof: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("asof")
  }

  /** Collection-like TableQuery object for table BakersHistory */
  lazy val BakersHistory = new TableQuery(tag => new BakersHistory(tag))

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
    *  @param accountId Database column account_id SqlType(varchar)
    *  @param change Database column change SqlType(numeric)
    *  @param level Database column level SqlType(numeric), Default(None)
    *  @param category Database column category SqlType(varchar), Default(None)
    *  @param operationGroupHash Database column operation_group_hash SqlType(varchar), Default(None)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param period Database column period SqlType(int4), Default(None) */
  case class BalanceUpdatesRow(
      id: Int,
      source: String,
      sourceId: Option[Int] = None,
      sourceHash: Option[String] = None,
      kind: String,
      accountId: String,
      change: scala.math.BigDecimal,
      level: Option[scala.math.BigDecimal] = None,
      category: Option[String] = None,
      operationGroupHash: Option[String] = None,
      blockId: String,
      blockLevel: Int,
      cycle: Option[Int] = None,
      period: Option[Int] = None
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
        <<[String],
        <<[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[String],
        <<?[String],
        <<[String],
        <<[Int],
        <<?[Int],
        <<?[Int]
      )
    )
  }

  /** Table description of table balance_updates. Objects of this class serve as prototypes for rows in queries. */
  class BalanceUpdates(_tableTag: Tag)
      extends profile.api.Table[BalanceUpdatesRow](_tableTag, Some("tezos"), "balance_updates") {
    def * =
      (
        id,
        source,
        sourceId,
        sourceHash,
        kind,
        accountId,
        change,
        level,
        category,
        operationGroupHash,
        blockId,
        blockLevel,
        cycle,
        period
      ) <> (BalanceUpdatesRow.tupled, BalanceUpdatesRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(id),
          Rep.Some(source),
          sourceId,
          sourceHash,
          Rep.Some(kind),
          Rep.Some(accountId),
          Rep.Some(change),
          level,
          category,
          operationGroupHash,
          Rep.Some(blockId),
          Rep.Some(blockLevel),
          cycle,
          period
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(
            _ =>
              BalanceUpdatesRow
                .tupled((_1.get, _2.get, _3, _4, _5.get, _6.get, _7.get, _8, _9, _10, _11.get, _12.get, _13, _14))
          )
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

    /** Database column account_id SqlType(varchar) */
    val accountId: Rep[String] = column[String]("account_id")

    /** Database column change SqlType(numeric) */
    val change: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("change")

    /** Database column level SqlType(numeric), Default(None) */
    val level: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("level", O.Default(None))

    /** Database column category SqlType(varchar), Default(None) */
    val category: Rep[Option[String]] = column[Option[String]]("category", O.Default(None))

    /** Database column operation_group_hash SqlType(varchar), Default(None) */
    val operationGroupHash: Rep[Option[String]] = column[Option[String]]("operation_group_hash", O.Default(None))

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Database column period SqlType(int4), Default(None) */
    val period: Rep[Option[Int]] = column[Option[Int]]("period", O.Default(None))

    /** Index over (accountId) (database name ix_balance_updates_account_id) */
    val index1 = index("ix_balance_updates_account_id", accountId)

    /** Index over (blockLevel) (database name ix_balance_updates_block_level) */
    val index2 = index("ix_balance_updates_block_level", blockLevel)

    /** Index over (operationGroupHash) (database name ix_balance_updates_op_group_hash) */
    val index3 = index("ix_balance_updates_op_group_hash", operationGroupHash)
  }

  /** Collection-like TableQuery object for table BalanceUpdates */
  lazy val BalanceUpdates = new TableQuery(tag => new BalanceUpdates(tag))

  /** Entity class storing rows of table BigMapContents
    *  @param bigMapId Database column big_map_id SqlType(numeric)
    *  @param key Database column key SqlType(varchar)
    *  @param keyHash Database column key_hash SqlType(varchar), Default(None)
    *  @param operationGroupId Database column operation_group_id SqlType(varchar), Default(None)
    *  @param value Database column value SqlType(varchar), Default(None) */
  case class BigMapContentsRow(
      bigMapId: scala.math.BigDecimal,
      key: String,
      keyHash: Option[String] = None,
      operationGroupId: Option[String] = None,
      value: Option[String] = None
  )

  /** GetResult implicit for fetching BigMapContentsRow objects using plain SQL queries */
  implicit def GetResultBigMapContentsRow(
      implicit e0: GR[scala.math.BigDecimal],
      e1: GR[String],
      e2: GR[Option[String]]
  ): GR[BigMapContentsRow] = GR { prs =>
    import prs._
    BigMapContentsRow.tupled((<<[scala.math.BigDecimal], <<[String], <<?[String], <<?[String], <<?[String]))
  }

  /** Table description of table big_map_contents. Objects of this class serve as prototypes for rows in queries. */
  class BigMapContents(_tableTag: Tag)
      extends profile.api.Table[BigMapContentsRow](_tableTag, Some("tezos"), "big_map_contents") {
    def * = (bigMapId, key, keyHash, operationGroupId, value) <> (BigMapContentsRow.tupled, BigMapContentsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(bigMapId), Rep.Some(key), keyHash, operationGroupId, value)).shaped.<>({ r =>
        import r._; _1.map(_ => BigMapContentsRow.tupled((_1.get, _2.get, _3, _4, _5)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column big_map_id SqlType(numeric) */
    val bigMapId: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("big_map_id")

    /** Database column key SqlType(varchar) */
    val key: Rep[String] = column[String]("key")

    /** Database column key_hash SqlType(varchar), Default(None) */
    val keyHash: Rep[Option[String]] = column[Option[String]]("key_hash", O.Default(None))

    /** Database column operation_group_id SqlType(varchar), Default(None) */
    val operationGroupId: Rep[Option[String]] = column[Option[String]]("operation_group_id", O.Default(None))

    /** Database column value SqlType(varchar), Default(None) */
    val value: Rep[Option[String]] = column[Option[String]]("value", O.Default(None))

    /** Primary key of BigMapContents (database name big_map_contents_pkey) */
    val pk = primaryKey("big_map_contents_pkey", (bigMapId, key))
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
    *  @param priority Database column priority SqlType(int4), Default(None)
    *  @param utcYear Database column utc_year SqlType(int4)
    *  @param utcMonth Database column utc_month SqlType(int4)
    *  @param utcDay Database column utc_day SqlType(int4)
    *  @param utcTime Database column utc_time SqlType(varchar) */
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
      priority: Option[Int] = None,
      utcYear: Int,
      utcMonth: Int,
      utcDay: Int,
      utcTime: String
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
      <<?[Int],
      <<[Int],
      <<[Int],
      <<[Int],
      <<[String]
    )
  }

  /** Table description of table blocks. Objects of this class serve as prototypes for rows in queries. */
  class Blocks(_tableTag: Tag) extends profile.api.Table[BlocksRow](_tableTag, Some("tezos"), "blocks") {
    def * =
      (level :: proto :: predecessor :: timestamp :: validationPass :: fitness :: context :: signature :: protocol :: chainId :: hash :: operationsHash :: periodKind :: currentExpectedQuorum :: activeProposal :: baker :: nonceHash :: consumedGas :: metaLevel :: metaLevelPosition :: metaCycle :: metaCyclePosition :: metaVotingPeriod :: metaVotingPeriodPosition :: expectedCommitment :: priority :: utcYear :: utcMonth :: utcDay :: utcTime :: HNil)
        .mapTo[BlocksRow]

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (Rep.Some(level) :: Rep.Some(proto) :: Rep.Some(predecessor) :: Rep.Some(timestamp) :: Rep.Some(validationPass) :: Rep
            .Some(fitness) :: context :: signature :: Rep.Some(protocol) :: chainId :: Rep.Some(hash) :: operationsHash :: periodKind :: currentExpectedQuorum :: activeProposal :: baker :: nonceHash :: consumedGas :: metaLevel :: metaLevelPosition :: metaCycle :: metaCyclePosition :: metaVotingPeriod :: metaVotingPeriodPosition :: expectedCommitment :: priority :: Rep
            .Some(utcYear) :: Rep.Some(utcMonth) :: Rep.Some(utcDay) :: Rep.Some(utcTime) :: HNil).shaped.<>(
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
            r(25).asInstanceOf[Option[Int]],
            r(26).asInstanceOf[Option[Int]].get,
            r(27).asInstanceOf[Option[Int]].get,
            r(28).asInstanceOf[Option[Int]].get,
            r(29).asInstanceOf[Option[String]].get
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

    /** Database column utc_year SqlType(int4) */
    val utcYear: Rep[Int] = column[Int]("utc_year")

    /** Database column utc_month SqlType(int4) */
    val utcMonth: Rep[Int] = column[Int]("utc_month")

    /** Database column utc_day SqlType(int4) */
    val utcDay: Rep[Int] = column[Int]("utc_day")

    /** Database column utc_time SqlType(varchar) */
    val utcTime: Rep[String] = column[String]("utc_time")

    /** Uniqueness Index over (hash) (database name blocks_hash_key) */
    val index1 = index("blocks_hash_key", hash :: HNil, unique = true)

    /** Index over (level) (database name ix_blocks_level) */
    val index2 = index("ix_blocks_level", level :: HNil)
  }

  /** Collection-like TableQuery object for table Blocks */
  lazy val Blocks = new TableQuery(tag => new Blocks(tag))

  /** Entity class storing rows of table EndorsingRights
    *  @param blockHash Database column block_hash SqlType(varchar), Default(None)
    *  @param level Database column level SqlType(int4)
    *  @param delegate Database column delegate SqlType(varchar)
    *  @param slot Database column slot SqlType(int4)
    *  @param estimatedTime Database column estimated_time SqlType(timestamp), Default(None)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param governancePeriod Database column governance_period SqlType(int4), Default(None)
    *  @param endorsedBlock Database column endorsed_block SqlType(int4), Default(None) */
  case class EndorsingRightsRow(
      blockHash: Option[String] = None,
      level: Int,
      delegate: String,
      slot: Int,
      estimatedTime: Option[java.sql.Timestamp] = None,
      cycle: Option[Int] = None,
      governancePeriod: Option[Int] = None,
      endorsedBlock: Option[Int] = None
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
    EndorsingRightsRow.tupled(
      (<<?[String], <<[Int], <<[String], <<[Int], <<?[java.sql.Timestamp], <<?[Int], <<?[Int], <<?[Int])
    )
  }

  /** Table description of table endorsing_rights. Objects of this class serve as prototypes for rows in queries. */
  class EndorsingRights(_tableTag: Tag)
      extends profile.api.Table[EndorsingRightsRow](_tableTag, Some("tezos"), "endorsing_rights") {
    def * =
      (blockHash, level, delegate, slot, estimatedTime, cycle, governancePeriod, endorsedBlock) <> (EndorsingRightsRow.tupled, EndorsingRightsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          blockHash,
          Rep.Some(level),
          Rep.Some(delegate),
          Rep.Some(slot),
          estimatedTime,
          cycle,
          governancePeriod,
          endorsedBlock
        )
      ).shaped.<>(
        { r =>
          import r._; _2.map(_ => EndorsingRightsRow.tupled((_1, _2.get, _3.get, _4.get, _5, _6, _7, _8)))
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

    /** Database column endorsed_block SqlType(int4), Default(None) */
    val endorsedBlock: Rep[Option[Int]] = column[Option[Int]]("endorsed_block", O.Default(None))

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

  /** Entity class storing rows of table Governance
    *  @param votingPeriod Database column voting_period SqlType(int4)
    *  @param votingPeriodKind Database column voting_period_kind SqlType(varchar)
    *  @param cycle Database column cycle SqlType(int4), Default(None)
    *  @param level Database column level SqlType(int4), Default(None)
    *  @param blockHash Database column block_hash SqlType(varchar)
    *  @param proposalHash Database column proposal_hash SqlType(varchar)
    *  @param yayCount Database column yay_count SqlType(int4), Default(None)
    *  @param nayCount Database column nay_count SqlType(int4), Default(None)
    *  @param passCount Database column pass_count SqlType(int4), Default(None)
    *  @param yayRolls Database column yay_rolls SqlType(numeric), Default(None)
    *  @param nayRolls Database column nay_rolls SqlType(numeric), Default(None)
    *  @param passRolls Database column pass_rolls SqlType(numeric), Default(None)
    *  @param totalRolls Database column total_rolls SqlType(numeric), Default(None)
    *  @param blockYayCount Database column block_yay_count SqlType(int4), Default(None)
    *  @param blockNayCount Database column block_nay_count SqlType(int4), Default(None)
    *  @param blockPassCount Database column block_pass_count SqlType(int4), Default(None)
    *  @param blockYayRolls Database column block_yay_rolls SqlType(numeric), Default(None)
    *  @param blockNayRolls Database column block_nay_rolls SqlType(numeric), Default(None)
    *  @param blockPassRolls Database column block_pass_rolls SqlType(numeric), Default(None) */
  case class GovernanceRow(
      votingPeriod: Int,
      votingPeriodKind: String,
      cycle: Option[Int] = None,
      level: Option[Int] = None,
      blockHash: String,
      proposalHash: String,
      yayCount: Option[Int] = None,
      nayCount: Option[Int] = None,
      passCount: Option[Int] = None,
      yayRolls: Option[scala.math.BigDecimal] = None,
      nayRolls: Option[scala.math.BigDecimal] = None,
      passRolls: Option[scala.math.BigDecimal] = None,
      totalRolls: Option[scala.math.BigDecimal] = None,
      blockYayCount: Option[Int] = None,
      blockNayCount: Option[Int] = None,
      blockPassCount: Option[Int] = None,
      blockYayRolls: Option[scala.math.BigDecimal] = None,
      blockNayRolls: Option[scala.math.BigDecimal] = None,
      blockPassRolls: Option[scala.math.BigDecimal] = None
  )

  /** GetResult implicit for fetching GovernanceRow objects using plain SQL queries */
  implicit def GetResultGovernanceRow(
      implicit e0: GR[Int],
      e1: GR[String],
      e2: GR[Option[Int]],
      e3: GR[Option[scala.math.BigDecimal]]
  ): GR[GovernanceRow] = GR { prs =>
    import prs._
    GovernanceRow.tupled(
      (
        <<[Int],
        <<[String],
        <<?[Int],
        <<?[Int],
        <<[String],
        <<[String],
        <<?[Int],
        <<?[Int],
        <<?[Int],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[Int],
        <<?[Int],
        <<?[Int],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal]
      )
    )
  }

  /** Table description of table governance. Objects of this class serve as prototypes for rows in queries. */
  class Governance(_tableTag: Tag) extends profile.api.Table[GovernanceRow](_tableTag, Some("tezos"), "governance") {
    def * =
      (
        votingPeriod,
        votingPeriodKind,
        cycle,
        level,
        blockHash,
        proposalHash,
        yayCount,
        nayCount,
        passCount,
        yayRolls,
        nayRolls,
        passRolls,
        totalRolls,
        blockYayCount,
        blockNayCount,
        blockPassCount,
        blockYayRolls,
        blockNayRolls,
        blockPassRolls
      ) <> (GovernanceRow.tupled, GovernanceRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(votingPeriod),
          Rep.Some(votingPeriodKind),
          cycle,
          level,
          Rep.Some(blockHash),
          Rep.Some(proposalHash),
          yayCount,
          nayCount,
          passCount,
          yayRolls,
          nayRolls,
          passRolls,
          totalRolls,
          blockYayCount,
          blockNayCount,
          blockPassCount,
          blockYayRolls,
          blockNayRolls,
          blockPassRolls
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(
            _ =>
              GovernanceRow.tupled(
                (_1.get, _2.get, _3, _4, _5.get, _6.get, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, _19)
              )
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column voting_period SqlType(int4) */
    val votingPeriod: Rep[Int] = column[Int]("voting_period")

    /** Database column voting_period_kind SqlType(varchar) */
    val votingPeriodKind: Rep[String] = column[String]("voting_period_kind")

    /** Database column cycle SqlType(int4), Default(None) */
    val cycle: Rep[Option[Int]] = column[Option[Int]]("cycle", O.Default(None))

    /** Database column level SqlType(int4), Default(None) */
    val level: Rep[Option[Int]] = column[Option[Int]]("level", O.Default(None))

    /** Database column block_hash SqlType(varchar) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column proposal_hash SqlType(varchar) */
    val proposalHash: Rep[String] = column[String]("proposal_hash")

    /** Database column yay_count SqlType(int4), Default(None) */
    val yayCount: Rep[Option[Int]] = column[Option[Int]]("yay_count", O.Default(None))

    /** Database column nay_count SqlType(int4), Default(None) */
    val nayCount: Rep[Option[Int]] = column[Option[Int]]("nay_count", O.Default(None))

    /** Database column pass_count SqlType(int4), Default(None) */
    val passCount: Rep[Option[Int]] = column[Option[Int]]("pass_count", O.Default(None))

    /** Database column yay_rolls SqlType(numeric), Default(None) */
    val yayRolls: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("yay_rolls", O.Default(None))

    /** Database column nay_rolls SqlType(numeric), Default(None) */
    val nayRolls: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("nay_rolls", O.Default(None))

    /** Database column pass_rolls SqlType(numeric), Default(None) */
    val passRolls: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("pass_rolls", O.Default(None))

    /** Database column total_rolls SqlType(numeric), Default(None) */
    val totalRolls: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("total_rolls", O.Default(None))

    /** Database column block_yay_count SqlType(int4), Default(None) */
    val blockYayCount: Rep[Option[Int]] = column[Option[Int]]("block_yay_count", O.Default(None))

    /** Database column block_nay_count SqlType(int4), Default(None) */
    val blockNayCount: Rep[Option[Int]] = column[Option[Int]]("block_nay_count", O.Default(None))

    /** Database column block_pass_count SqlType(int4), Default(None) */
    val blockPassCount: Rep[Option[Int]] = column[Option[Int]]("block_pass_count", O.Default(None))

    /** Database column block_yay_rolls SqlType(numeric), Default(None) */
    val blockYayRolls: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("block_yay_rolls", O.Default(None))

    /** Database column block_nay_rolls SqlType(numeric), Default(None) */
    val blockNayRolls: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("block_nay_rolls", O.Default(None))

    /** Database column block_pass_rolls SqlType(numeric), Default(None) */
    val blockPassRolls: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("block_pass_rolls", O.Default(None))

    /** Primary key of Governance (database name governance_pkey) */
    val pk = primaryKey("governance_pkey", (blockHash, proposalHash))

    /** Index over (blockHash) (database name governance_block_hash_idx) */
    val index1 = index("governance_block_hash_idx", blockHash)

    /** Index over (proposalHash) (database name governance_proposal_hash_idx) */
    val index2 = index("governance_proposal_hash_idx", proposalHash)
  }

  /** Collection-like TableQuery object for table Governance */
  lazy val Governance = new TableQuery(tag => new Governance(tag))

  /** Entity class storing rows of table KnownAddresses
    *  @param address Database column address SqlType(varchar)
    *  @param alias Database column alias SqlType(varchar) */
  case class KnownAddressesRow(address: String, alias: String)

  /** GetResult implicit for fetching KnownAddressesRow objects using plain SQL queries */
  implicit def GetResultKnownAddressesRow(implicit e0: GR[String]): GR[KnownAddressesRow] = GR { prs =>
    import prs._
    KnownAddressesRow.tupled((<<[String], <<[String]))
  }

  /** Table description of table known_addresses. Objects of this class serve as prototypes for rows in queries. */
  class KnownAddresses(_tableTag: Tag)
      extends profile.api.Table[KnownAddressesRow](_tableTag, Some("tezos"), "known_addresses") {
    def * = (address, alias) <> (KnownAddressesRow.tupled, KnownAddressesRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(address), Rep.Some(alias))).shaped.<>({ r =>
        import r._; _1.map(_ => KnownAddressesRow.tupled((_1.get, _2.get)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column address SqlType(varchar) */
    val address: Rep[String] = column[String]("address")

    /** Database column alias SqlType(varchar) */
    val alias: Rep[String] = column[String]("alias")
  }

  /** Collection-like TableQuery object for table KnownAddresses */
  lazy val KnownAddresses = new TableQuery(tag => new KnownAddresses(tag))

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
    *  @param parametersEntrypoints Database column parameters_entrypoints SqlType(varchar), Default(None)
    *  @param parametersMicheline Database column parameters_micheline SqlType(varchar), Default(None)
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
    *  @param ballotPeriod Database column ballot_period SqlType(int4), Default(None)
    *  @param timestamp Database column timestamp SqlType(timestamp)
    *  @param errors Database column errors SqlType(varchar), Default(None)
    *  @param utcYear Database column utc_year SqlType(int4)
    *  @param utcMonth Database column utc_month SqlType(int4)
    *  @param utcDay Database column utc_day SqlType(int4)
    *  @param utcTime Database column utc_time SqlType(varchar) */
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
      parametersEntrypoints: Option[String] = None,
      parametersMicheline: Option[String] = None,
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
      ballotPeriod: Option[Int] = None,
      timestamp: java.sql.Timestamp,
      errors: Option[String] = None,
      utcYear: Int,
      utcMonth: Int,
      utcDay: Int,
      utcTime: String
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
      <<?[Int],
      <<[java.sql.Timestamp],
      <<?[String],
      <<[Int],
      <<[Int],
      <<[Int],
      <<[String]
    )
  }

  /** Table description of table operations. Objects of this class serve as prototypes for rows in queries. */
  class Operations(_tableTag: Tag) extends profile.api.Table[OperationsRow](_tableTag, Some("tezos"), "operations") {
    def * =
      (branch :: numberOfSlots :: cycle :: operationId :: operationGroupHash :: kind :: level :: delegate :: slots :: nonce :: pkh :: secret :: source :: fee :: counter :: gasLimit :: storageLimit :: publicKey :: amount :: destination :: parameters :: parametersEntrypoints :: parametersMicheline :: managerPubkey :: balance :: proposal :: spendable :: delegatable :: script :: storage :: status :: consumedGas :: storageSize :: paidStorageSizeDiff :: originatedContracts :: blockHash :: blockLevel :: ballot :: internal :: period :: ballotPeriod :: timestamp :: errors :: utcYear :: utcMonth :: utcDay :: utcTime :: HNil)
        .mapTo[OperationsRow]

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (branch :: numberOfSlots :: cycle :: Rep.Some(operationId) :: Rep.Some(operationGroupHash) :: Rep.Some(kind) :: level :: delegate :: slots :: nonce :: pkh :: secret :: source :: fee :: counter :: gasLimit :: storageLimit :: publicKey :: amount :: destination :: parameters :: parametersEntrypoints :: parametersMicheline :: managerPubkey :: balance :: proposal :: spendable :: delegatable :: script :: storage :: status :: consumedGas :: storageSize :: paidStorageSizeDiff :: originatedContracts :: Rep
            .Some(blockHash) :: Rep.Some(blockLevel) :: ballot :: Rep.Some(internal) :: period :: ballotPeriod :: Rep
            .Some(timestamp) :: errors :: Rep.Some(utcYear) :: Rep.Some(utcMonth) :: Rep.Some(utcDay) :: Rep.Some(
            utcTime
          ) :: HNil).shaped.<>(
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
            r(22).asInstanceOf[Option[String]],
            r(23).asInstanceOf[Option[String]],
            r(24).asInstanceOf[Option[scala.math.BigDecimal]],
            r(25).asInstanceOf[Option[String]],
            r(26).asInstanceOf[Option[Boolean]],
            r(27).asInstanceOf[Option[Boolean]],
            r(28).asInstanceOf[Option[String]],
            r(29).asInstanceOf[Option[String]],
            r(30).asInstanceOf[Option[String]],
            r(31).asInstanceOf[Option[scala.math.BigDecimal]],
            r(32).asInstanceOf[Option[scala.math.BigDecimal]],
            r(33).asInstanceOf[Option[scala.math.BigDecimal]],
            r(34).asInstanceOf[Option[String]],
            r(35).asInstanceOf[Option[String]].get,
            r(36).asInstanceOf[Option[Int]].get,
            r(37).asInstanceOf[Option[String]],
            r(38).asInstanceOf[Option[Boolean]].get,
            r(39).asInstanceOf[Option[Int]],
            r(40).asInstanceOf[Option[Int]],
            r(41).asInstanceOf[Option[java.sql.Timestamp]].get,
            r(42).asInstanceOf[Option[String]],
            r(43).asInstanceOf[Option[Int]].get,
            r(44).asInstanceOf[Option[Int]].get,
            r(45).asInstanceOf[Option[Int]].get,
            r(46).asInstanceOf[Option[String]].get
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

    /** Database column parameters_entrypoints SqlType(varchar), Default(None) */
    val parametersEntrypoints: Rep[Option[String]] = column[Option[String]]("parameters_entrypoints", O.Default(None))

    /** Database column parameters_micheline SqlType(varchar), Default(None) */
    val parametersMicheline: Rep[Option[String]] = column[Option[String]]("parameters_micheline", O.Default(None))

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

    /** Database column ballot_period SqlType(int4), Default(None) */
    val ballotPeriod: Rep[Option[Int]] = column[Option[Int]]("ballot_period", O.Default(None))

    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")

    /** Database column errors SqlType(varchar), Default(None) */
    val errors: Rep[Option[String]] = column[Option[String]]("errors", O.Default(None))

    /** Database column utc_year SqlType(int4) */
    val utcYear: Rep[Int] = column[Int]("utc_year")

    /** Database column utc_month SqlType(int4) */
    val utcMonth: Rep[Int] = column[Int]("utc_month")

    /** Database column utc_day SqlType(int4) */
    val utcDay: Rep[Int] = column[Int]("utc_day")

    /** Database column utc_time SqlType(varchar) */
    val utcTime: Rep[String] = column[String]("utc_time")

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

    /** Index over (cycle) (database name ix_operations_cycle) */
    val index2 = index("ix_operations_cycle", cycle :: HNil)

    /** Index over (delegate) (database name ix_operations_delegate) */
    val index3 = index("ix_operations_delegate", delegate :: HNil)

    /** Index over (destination) (database name ix_operations_destination) */
    val index4 = index("ix_operations_destination", destination :: HNil)

    /** Index over (kind) (database name ix_operations_kind) */
    val index5 = index("ix_operations_kind", kind :: HNil)

    /** Index over (source) (database name ix_operations_source) */
    val index6 = index("ix_operations_source", source :: HNil)

    /** Index over (timestamp) (database name ix_operations_timestamp) */
    val index7 = index("ix_operations_timestamp", timestamp :: HNil)
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

  /** Entity class storing rows of table RegisteredTokens
    *  @param id Database column id SqlType(int4), PrimaryKey
    *  @param name Database column name SqlType(text)
    *  @param contractType Database column contract_type SqlType(text)
    *  @param accountId Database column account_id SqlType(text) */
  case class RegisteredTokensRow(id: Int, name: String, contractType: String, accountId: String)

  /** GetResult implicit for fetching RegisteredTokensRow objects using plain SQL queries */
  implicit def GetResultRegisteredTokensRow(implicit e0: GR[Int], e1: GR[String]): GR[RegisteredTokensRow] = GR { prs =>
    import prs._
    RegisteredTokensRow.tupled((<<[Int], <<[String], <<[String], <<[String]))
  }

  /** Table description of table registered_tokens. Objects of this class serve as prototypes for rows in queries. */
  class RegisteredTokens(_tableTag: Tag)
      extends profile.api.Table[RegisteredTokensRow](_tableTag, Some("tezos"), "registered_tokens") {
    def * = (id, name, contractType, accountId) <> (RegisteredTokensRow.tupled, RegisteredTokensRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(id), Rep.Some(name), Rep.Some(contractType), Rep.Some(accountId))).shaped.<>(
        { r =>
          import r._; _1.map(_ => RegisteredTokensRow.tupled((_1.get, _2.get, _3.get, _4.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column id SqlType(int4), PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.PrimaryKey)

    /** Database column name SqlType(text) */
    val name: Rep[String] = column[String]("name")

    /** Database column contract_type SqlType(text) */
    val contractType: Rep[String] = column[String]("contract_type")

    /** Database column account_id SqlType(text) */
    val accountId: Rep[String] = column[String]("account_id")
  }

  /** Collection-like TableQuery object for table RegisteredTokens */
  lazy val RegisteredTokens = new TableQuery(tag => new RegisteredTokens(tag))

  /** Entity class storing rows of table TezosNames
    *  @param name Database column name SqlType(varchar), PrimaryKey
    *  @param owner Database column owner SqlType(varchar), Default(None)
    *  @param resolver Database column resolver SqlType(varchar), Default(None)
    *  @param registeredAt Database column registered_at SqlType(timestamp), Default(None)
    *  @param registrationPeriod Database column registration_period SqlType(int4), Default(None)
    *  @param modified Database column modified SqlType(bool), Default(None) */
  case class TezosNamesRow(
      name: String,
      owner: Option[String] = None,
      resolver: Option[String] = None,
      registeredAt: Option[java.sql.Timestamp] = None,
      registrationPeriod: Option[Int] = None,
      modified: Option[Boolean] = None
  )

  /** GetResult implicit for fetching TezosNamesRow objects using plain SQL queries */
  implicit def GetResultTezosNamesRow(
      implicit e0: GR[String],
      e1: GR[Option[String]],
      e2: GR[Option[java.sql.Timestamp]],
      e3: GR[Option[Int]],
      e4: GR[Option[Boolean]]
  ): GR[TezosNamesRow] = GR { prs =>
    import prs._
    TezosNamesRow.tupled((<<[String], <<?[String], <<?[String], <<?[java.sql.Timestamp], <<?[Int], <<?[Boolean]))
  }

  /** Table description of table tezos_names. Objects of this class serve as prototypes for rows in queries. */
  class TezosNames(_tableTag: Tag) extends profile.api.Table[TezosNamesRow](_tableTag, Some("tezos"), "tezos_names") {
    def * =
      (name, owner, resolver, registeredAt, registrationPeriod, modified) <> (TezosNamesRow.tupled, TezosNamesRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(name), owner, resolver, registeredAt, registrationPeriod, modified)).shaped.<>({ r =>
        import r._; _1.map(_ => TezosNamesRow.tupled((_1.get, _2, _3, _4, _5, _6)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column name SqlType(varchar), PrimaryKey */
    val name: Rep[String] = column[String]("name", O.PrimaryKey)

    /** Database column owner SqlType(varchar), Default(None) */
    val owner: Rep[Option[String]] = column[Option[String]]("owner", O.Default(None))

    /** Database column resolver SqlType(varchar), Default(None) */
    val resolver: Rep[Option[String]] = column[Option[String]]("resolver", O.Default(None))

    /** Database column registered_at SqlType(timestamp), Default(None) */
    val registeredAt: Rep[Option[java.sql.Timestamp]] =
      column[Option[java.sql.Timestamp]]("registered_at", O.Default(None))

    /** Database column registration_period SqlType(int4), Default(None) */
    val registrationPeriod: Rep[Option[Int]] = column[Option[Int]]("registration_period", O.Default(None))

    /** Database column modified SqlType(bool), Default(None) */
    val modified: Rep[Option[Boolean]] = column[Option[Boolean]]("modified", O.Default(None))

    /** Index over (owner) (database name tezos_names_owner_idx) */
    val index1 = index("tezos_names_owner_idx", owner)

    /** Index over (resolver) (database name tezos_names_resolver_idx) */
    val index2 = index("tezos_names_resolver_idx", resolver)
  }

  /** Collection-like TableQuery object for table TezosNames */
  lazy val TezosNames = new TableQuery(tag => new TezosNames(tag))

  /** Entity class storing rows of table TokenBalances
    *  @param tokenId Database column token_id SqlType(int4)
    *  @param address Database column address SqlType(text)
    *  @param balance Database column balance SqlType(numeric)
    *  @param blockId Database column block_id SqlType(varchar)
    *  @param blockLevel Database column block_level SqlType(numeric), Default(-1)
    *  @param asof Database column asof SqlType(timestamp) */
  case class TokenBalancesRow(
      tokenId: Int,
      address: String,
      balance: scala.math.BigDecimal,
      blockId: String,
      blockLevel: scala.math.BigDecimal = scala.math.BigDecimal("-1"),
      asof: java.sql.Timestamp
  )

  /** GetResult implicit for fetching TokenBalancesRow objects using plain SQL queries */
  implicit def GetResultTokenBalancesRow(
      implicit e0: GR[Int],
      e1: GR[String],
      e2: GR[scala.math.BigDecimal],
      e3: GR[java.sql.Timestamp]
  ): GR[TokenBalancesRow] = GR { prs =>
    import prs._
    TokenBalancesRow.tupled(
      (<<[Int], <<[String], <<[scala.math.BigDecimal], <<[String], <<[scala.math.BigDecimal], <<[java.sql.Timestamp])
    )
  }

  /** Table description of table token_balances. Objects of this class serve as prototypes for rows in queries. */
  class TokenBalances(_tableTag: Tag)
      extends profile.api.Table[TokenBalancesRow](_tableTag, Some("tezos"), "token_balances") {
    def * =
      (tokenId, address, balance, blockId, blockLevel, asof) <> (TokenBalancesRow.tupled, TokenBalancesRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(tokenId),
          Rep.Some(address),
          Rep.Some(balance),
          Rep.Some(blockId),
          Rep.Some(blockLevel),
          Rep.Some(asof)
        )
      ).shaped.<>(
        { r =>
          import r._; _1.map(_ => TokenBalancesRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column token_id SqlType(int4) */
    val tokenId: Rep[Int] = column[Int]("token_id")

    /** Database column address SqlType(text) */
    val address: Rep[String] = column[String]("address")

    /** Database column balance SqlType(numeric) */
    val balance: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("balance")

    /** Database column block_id SqlType(varchar) */
    val blockId: Rep[String] = column[String]("block_id")

    /** Database column block_level SqlType(numeric), Default(-1) */
    val blockLevel: Rep[scala.math.BigDecimal] =
      column[scala.math.BigDecimal]("block_level", O.Default(scala.math.BigDecimal("-1")))

    /** Database column asof SqlType(timestamp) */
    val asof: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("asof")

    /** Primary key of TokenBalances (database name token_balances_pkey) */
    val pk = primaryKey("token_balances_pkey", (tokenId, address, blockLevel))
  }

  /** Collection-like TableQuery object for table TokenBalances */
  lazy val TokenBalances = new TableQuery(tag => new TokenBalances(tag))
}
