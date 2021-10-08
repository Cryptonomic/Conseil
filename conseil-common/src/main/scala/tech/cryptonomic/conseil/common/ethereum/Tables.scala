package tech.cryptonomic.conseil.common.ethereum

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
  lazy val schema: profile.SchemaDescription = Array(
    Accounts.schema,
    AccountsHistory.schema,
    Blocks.schema,
    Logs.schema,
    Receipts.schema,
    TokensHistory.schema,
    TokenTransfers.schema,
    Transactions.schema
  ).reduceLeft(_ ++ _)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Accounts
    *  @param address Database column address SqlType(text), PrimaryKey
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param timestamp Database column timestamp SqlType(timestamp), Default(None)
    *  @param balance Database column balance SqlType(numeric)
    *  @param bytecode Database column bytecode SqlType(text), Default(None)
    *  @param bytecodeHash Database column bytecode_hash SqlType(text), Default(None)
    *  @param tokenStandard Database column token_standard SqlType(text), Default(None)
    *  @param name Database column name SqlType(text), Default(None)
    *  @param symbol Database column symbol SqlType(text), Default(None)
    *  @param decimals Database column decimals SqlType(int4), Default(None)
    *  @param totalSupply Database column total_supply SqlType(numeric), Default(None) */
  case class AccountsRow(
      address: String,
      blockHash: String,
      blockLevel: Int,
      timestamp: Option[java.sql.Timestamp],
      balance: scala.math.BigDecimal,
      bytecode: Option[String],
      bytecodeHash: Option[String],
      tokenStandard: Option[String],
      name: Option[String],
      symbol: Option[String],
      decimals: Option[Int],
      totalSupply: Option[scala.math.BigDecimal]
  )

  /** GetResult implicit for fetching AccountsRow objects using plain SQL queries */
  implicit def GetResultAccountsRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[Option[java.sql.Timestamp]],
      e3: GR[scala.math.BigDecimal],
      e4: GR[Option[String]],
      e5: GR[Option[Int]],
      e6: GR[Option[scala.math.BigDecimal]]
  ): GR[AccountsRow] = GR { prs =>
    import prs._
    AccountsRow.tupled(
      (
        <<[String],
        <<[String],
        <<[Int],
        <<?[java.sql.Timestamp],
        <<[scala.math.BigDecimal],
        <<?[String],
        <<?[String],
        <<?[String],
        <<?[String],
        <<?[String],
        <<?[Int],
        <<?[scala.math.BigDecimal]
      )
    )
  }

  /** Table description of table accounts. Objects of this class serve as prototypes for rows in queries. */
  class Accounts(_tableTag: Tag) extends profile.api.Table[AccountsRow](_tableTag, Some("ethereum"), "accounts") {
    def * =
      (
        address,
        blockHash,
        blockLevel,
        timestamp,
        balance,
        bytecode,
        bytecodeHash,
        tokenStandard,
        name,
        symbol,
        decimals,
        totalSupply
      ) <> (AccountsRow.tupled, AccountsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(address),
          Rep.Some(blockHash),
          Rep.Some(blockLevel),
          timestamp,
          Rep.Some(balance),
          bytecode,
          bytecodeHash,
          tokenStandard,
          name,
          symbol,
          decimals,
          totalSupply
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ => AccountsRow.tupled((_1.get, _2.get, _3.get, _4, _5.get, _6, _7, _8, _9, _10, _11, _12)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column address SqlType(text), PrimaryKey */
    val address: Rep[String] = column[String]("address", O.PrimaryKey)

    /** Database column block_hash SqlType(text) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column timestamp SqlType(timestamp), Default(None) */
    val timestamp: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("timestamp", O.Default(None))

    /** Database column balance SqlType(numeric) */
    val balance: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("balance")

    /** Database column bytecode SqlType(text), Default(None) */
    val bytecode: Rep[Option[String]] = column[Option[String]]("bytecode", O.Default(None))

    /** Database column bytecode_hash SqlType(text), Default(None) */
    val bytecodeHash: Rep[Option[String]] = column[Option[String]]("bytecode_hash", O.Default(None))

    /** Database column token_standard SqlType(text), Default(None) */
    val tokenStandard: Rep[Option[String]] = column[Option[String]]("token_standard", O.Default(None))

    /** Database column name SqlType(text), Default(None) */
    val name: Rep[Option[String]] = column[Option[String]]("name", O.Default(None))

    /** Database column symbol SqlType(text), Default(None) */
    val symbol: Rep[Option[String]] = column[Option[String]]("symbol", O.Default(None))

    /** Database column decimals SqlType(int4), Default(None) */
    val decimals: Rep[Option[Int]] = column[Option[Int]]("decimals", O.Default(None))

    /** Database column total_supply SqlType(numeric), Default(None) */
    val totalSupply: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("total_supply", O.Default(None))

    /** Index over (tokenStandard) (database name ix_accounts_token_standard) */
    val index1 = index("ix_accounts_token_standard", tokenStandard)
  }

  /** Collection-like TableQuery object for table Accounts */
  lazy val Accounts = new TableQuery(tag => new Accounts(tag))

  /** Entity class storing rows of table AccountsHistory
    *  @param address Database column address SqlType(text)
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param balance Database column balance SqlType(numeric)
    *  @param asof Database column asof SqlType(timestamp) */
  case class AccountsHistoryRow(
      address: String,
      blockHash: String,
      blockLevel: Int,
      balance: scala.math.BigDecimal,
      asof: java.sql.Timestamp
  )

  /** GetResult implicit for fetching AccountsHistoryRow objects using plain SQL queries */
  implicit def GetResultAccountsHistoryRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[scala.math.BigDecimal],
      e3: GR[java.sql.Timestamp]
  ): GR[AccountsHistoryRow] = GR { prs =>
    import prs._
    AccountsHistoryRow.tupled((<<[String], <<[String], <<[Int], <<[scala.math.BigDecimal], <<[java.sql.Timestamp]))
  }

  /** Table description of table accounts_history. Objects of this class serve as prototypes for rows in queries. */
  class AccountsHistory(_tableTag: Tag)
      extends profile.api.Table[AccountsHistoryRow](_tableTag, Some("ethereum"), "accounts_history") {
    def * = (address, blockHash, blockLevel, balance, asof) <> (AccountsHistoryRow.tupled, AccountsHistoryRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(address), Rep.Some(blockHash), Rep.Some(blockLevel), Rep.Some(balance), Rep.Some(asof))).shaped.<>(
        { r =>
          import r._; _1.map(_ => AccountsHistoryRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column address SqlType(text) */
    val address: Rep[String] = column[String]("address")

    /** Database column block_hash SqlType(text) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column balance SqlType(numeric) */
    val balance: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("balance")

    /** Database column asof SqlType(timestamp) */
    val asof: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("asof")

    /** Primary key of AccountsHistory (database name accounts_history_pkey) */
    val pk = primaryKey("accounts_history_pkey", (address, blockLevel))

    /** Index over (address) (database name ix_accounts_history_address) */
    val index1 = index("ix_accounts_history_address", address)

    /** Index over (blockLevel) (database name ix_accounts_history_block_level) */
    val index2 = index("ix_accounts_history_block_level", blockLevel)
  }

  /** Collection-like TableQuery object for table AccountsHistory */
  lazy val AccountsHistory = new TableQuery(tag => new AccountsHistory(tag))

  /** Entity class storing rows of table Blocks
    *  @param hash Database column hash SqlType(text), PrimaryKey
    *  @param level Database column level SqlType(int4)
    *  @param difficulty Database column difficulty SqlType(numeric)
    *  @param extraData Database column extra_data SqlType(text)
    *  @param gasLimit Database column gas_limit SqlType(numeric)
    *  @param gasUsed Database column gas_used SqlType(numeric)
    *  @param logsBloom Database column logs_bloom SqlType(text)
    *  @param miner Database column miner SqlType(text)
    *  @param mixHash Database column mix_hash SqlType(text)
    *  @param nonce Database column nonce SqlType(text)
    *  @param parentHash Database column parent_hash SqlType(text), Default(None)
    *  @param receiptsRoot Database column receipts_root SqlType(text)
    *  @param sha3Uncles Database column sha3_uncles SqlType(text)
    *  @param size Database column size SqlType(int4)
    *  @param stateRoot Database column state_root SqlType(text)
    *  @param totalDifficulty Database column total_difficulty SqlType(numeric)
    *  @param transactionsRoot Database column transactions_root SqlType(text)
    *  @param uncles Database column uncles SqlType(text), Default(None)
    *  @param timestamp Database column timestamp SqlType(timestamp) */
  case class BlocksRow(
      hash: String,
      level: Int,
      difficulty: scala.math.BigDecimal,
      extraData: String,
      gasLimit: scala.math.BigDecimal,
      gasUsed: scala.math.BigDecimal,
      logsBloom: String,
      miner: String,
      mixHash: String,
      nonce: String,
      parentHash: Option[String] = None,
      receiptsRoot: String,
      sha3Uncles: String,
      size: Int,
      stateRoot: String,
      totalDifficulty: scala.math.BigDecimal,
      transactionsRoot: String,
      uncles: Option[String] = None,
      timestamp: java.sql.Timestamp
  )

  /** GetResult implicit for fetching BlocksRow objects using plain SQL queries */
  implicit def GetResultBlocksRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[scala.math.BigDecimal],
      e3: GR[Option[String]],
      e4: GR[java.sql.Timestamp]
  ): GR[BlocksRow] = GR { prs =>
    import prs._
    BlocksRow.tupled(
      (
        <<[String],
        <<[Int],
        <<[scala.math.BigDecimal],
        <<[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<?[String],
        <<[String],
        <<[String],
        <<[Int],
        <<[String],
        <<[scala.math.BigDecimal],
        <<[String],
        <<?[String],
        <<[java.sql.Timestamp]
      )
    )
  }

  /** Table description of table blocks. Objects of this class serve as prototypes for rows in queries. */
  class Blocks(_tableTag: Tag) extends profile.api.Table[BlocksRow](_tableTag, Some("ethereum"), "blocks") {
    def * =
      (
        hash,
        level,
        difficulty,
        extraData,
        gasLimit,
        gasUsed,
        logsBloom,
        miner,
        mixHash,
        nonce,
        parentHash,
        receiptsRoot,
        sha3Uncles,
        size,
        stateRoot,
        totalDifficulty,
        transactionsRoot,
        uncles,
        timestamp
      ) <> (BlocksRow.tupled, BlocksRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(hash),
          Rep.Some(level),
          Rep.Some(difficulty),
          Rep.Some(extraData),
          Rep.Some(gasLimit),
          Rep.Some(gasUsed),
          Rep.Some(logsBloom),
          Rep.Some(miner),
          Rep.Some(mixHash),
          Rep.Some(nonce),
          parentHash,
          Rep.Some(receiptsRoot),
          Rep.Some(sha3Uncles),
          Rep.Some(size),
          Rep.Some(stateRoot),
          Rep.Some(totalDifficulty),
          Rep.Some(transactionsRoot),
          uncles,
          Rep.Some(timestamp)
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(
            _ =>
              BlocksRow.tupled(
                (
                  _1.get,
                  _2.get,
                  _3.get,
                  _4.get,
                  _5.get,
                  _6.get,
                  _7.get,
                  _8.get,
                  _9.get,
                  _10.get,
                  _11,
                  _12.get,
                  _13.get,
                  _14.get,
                  _15.get,
                  _16.get,
                  _17.get,
                  _18,
                  _19.get
                )
              )
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column hash SqlType(text), PrimaryKey */
    val hash: Rep[String] = column[String]("hash", O.PrimaryKey)

    /** Database column level SqlType(int4) */
    val level: Rep[Int] = column[Int]("level")

    /** Database column difficulty SqlType(numeric) */
    val difficulty: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("difficulty")

    /** Database column extra_data SqlType(text) */
    val extraData: Rep[String] = column[String]("extra_data")

    /** Database column gas_limit SqlType(numeric) */
    val gasLimit: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("gas_limit")

    /** Database column gas_used SqlType(numeric) */
    val gasUsed: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("gas_used")

    /** Database column logs_bloom SqlType(text) */
    val logsBloom: Rep[String] = column[String]("logs_bloom")

    /** Database column miner SqlType(text) */
    val miner: Rep[String] = column[String]("miner")

    /** Database column mix_hash SqlType(text) */
    val mixHash: Rep[String] = column[String]("mix_hash")

    /** Database column nonce SqlType(text) */
    val nonce: Rep[String] = column[String]("nonce")

    /** Database column parent_hash SqlType(text), Default(None) */
    val parentHash: Rep[Option[String]] = column[Option[String]]("parent_hash", O.Default(None))

    /** Database column receipts_root SqlType(text) */
    val receiptsRoot: Rep[String] = column[String]("receipts_root")

    /** Database column sha3_uncles SqlType(text) */
    val sha3Uncles: Rep[String] = column[String]("sha3_uncles")

    /** Database column size SqlType(int4) */
    val size: Rep[Int] = column[Int]("size")

    /** Database column state_root SqlType(text) */
    val stateRoot: Rep[String] = column[String]("state_root")

    /** Database column total_difficulty SqlType(numeric) */
    val totalDifficulty: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("total_difficulty")

    /** Database column transactions_root SqlType(text) */
    val transactionsRoot: Rep[String] = column[String]("transactions_root")

    /** Database column uncles SqlType(text), Default(None) */
    val uncles: Rep[Option[String]] = column[Option[String]]("uncles", O.Default(None))

    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")

    /** Index over (level) (database name ix_blocks_level) */
    val index1 = index("ix_blocks_level", level)

    /** Index over (timestamp) (database name ix_blocks_timestamp) */
    val index2 = index("ix_blocks_timestamp", timestamp)
  }

  /** Collection-like TableQuery object for table Blocks */
  lazy val Blocks = new TableQuery(tag => new Blocks(tag))

  /** Entity class storing rows of table Logs
    *  @param address Database column address SqlType(text)
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param timestamp Database column timestamp SqlType(timestamp), Default(None)
    *  @param data Database column data SqlType(text)
    *  @param logIndex Database column log_index SqlType(int4)
    *  @param removed Database column removed SqlType(bool)
    *  @param topics Database column topics SqlType(text)
    *  @param transactionHash Database column transaction_hash SqlType(text)
    *  @param transactionIndex Database column transaction_index SqlType(int4) */
  case class LogsRow(
      address: String,
      blockHash: String,
      blockLevel: Int,
      timestamp: Option[java.sql.Timestamp] = None,
      data: String,
      logIndex: Int,
      removed: Boolean,
      topics: String,
      transactionHash: String,
      transactionIndex: Int
  )

  /** GetResult implicit for fetching LogsRow objects using plain SQL queries */
  implicit def GetResultLogsRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[Option[java.sql.Timestamp]],
      e3: GR[Boolean]
  ): GR[LogsRow] = GR { prs =>
    import prs._
    LogsRow.tupled(
      (
        <<[String],
        <<[String],
        <<[Int],
        <<?[java.sql.Timestamp],
        <<[String],
        <<[Int],
        <<[Boolean],
        <<[String],
        <<[String],
        <<[Int]
      )
    )
  }

  /** Table description of table logs. Objects of this class serve as prototypes for rows in queries. */
  class Logs(_tableTag: Tag) extends profile.api.Table[LogsRow](_tableTag, Some("ethereum"), "logs") {
    def * =
      (address, blockHash, blockLevel, timestamp, data, logIndex, removed, topics, transactionHash, transactionIndex) <> (LogsRow.tupled, LogsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(address),
          Rep.Some(blockHash),
          Rep.Some(blockLevel),
          timestamp,
          Rep.Some(data),
          Rep.Some(logIndex),
          Rep.Some(removed),
          Rep.Some(topics),
          Rep.Some(transactionHash),
          Rep.Some(transactionIndex)
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ => LogsRow.tupled((_1.get, _2.get, _3.get, _4, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column address SqlType(text) */
    val address: Rep[String] = column[String]("address")

    /** Database column block_hash SqlType(text) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column timestamp SqlType(timestamp), Default(None) */
    val timestamp: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("timestamp", O.Default(None))

    /** Database column data SqlType(text) */
    val data: Rep[String] = column[String]("data")

    /** Database column log_index SqlType(int4) */
    val logIndex: Rep[Int] = column[Int]("log_index")

    /** Database column removed SqlType(bool) */
    val removed: Rep[Boolean] = column[Boolean]("removed")

    /** Database column topics SqlType(text) */
    val topics: Rep[String] = column[String]("topics")

    /** Database column transaction_hash SqlType(text) */
    val transactionHash: Rep[String] = column[String]("transaction_hash")

    /** Database column transaction_index SqlType(int4) */
    val transactionIndex: Rep[Int] = column[Int]("transaction_index")

    /** Foreign key referencing Blocks (database name ethereum_logs_block_hash_fkey) */
    lazy val blocksFk = foreignKey("ethereum_logs_block_hash_fkey", blockHash, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (address) (database name ix_logs_address) */
    val index1 = index("ix_logs_address", address)

    /** Index over (blockLevel) (database name ix_logs_block_level) */
    val index2 = index("ix_logs_block_level", blockLevel)

    /** Index over (transactionHash) (database name ix_logs_hash) */
    val index3 = index("ix_logs_hash", transactionHash)
  }

  /** Collection-like TableQuery object for table Logs */
  lazy val Logs = new TableQuery(tag => new Logs(tag))

  /** Entity class storing rows of table Receipts
    *  @param transactionHash Database column transaction_hash SqlType(text)
    *  @param transactionIndex Database column transaction_index SqlType(int4)
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param timestamp Database column timestamp SqlType(timestamp), Default(None)
    *  @param contractAddress Database column contract_address SqlType(text), Default(None)
    *  @param cumulativeGasUsed Database column cumulative_gas_used SqlType(numeric)
    *  @param gasUsed Database column gas_used SqlType(numeric)
    *  @param logsBloom Database column logs_bloom SqlType(text)
    *  @param status Database column status SqlType(text), Default(None)
    *  @param root Database column root SqlType(text), Default(None) */
  case class ReceiptsRow(
      transactionHash: String,
      transactionIndex: Int,
      blockHash: String,
      blockLevel: Int,
      timestamp: Option[java.sql.Timestamp] = None,
      contractAddress: Option[String] = None,
      cumulativeGasUsed: scala.math.BigDecimal,
      gasUsed: scala.math.BigDecimal,
      logsBloom: String,
      status: Option[String] = None,
      root: Option[String] = None
  )

  /** GetResult implicit for fetching ReceiptsRow objects using plain SQL queries */
  implicit def GetResultReceiptsRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[Option[java.sql.Timestamp]],
      e3: GR[Option[String]],
      e4: GR[scala.math.BigDecimal]
  ): GR[ReceiptsRow] = GR { prs =>
    import prs._
    ReceiptsRow.tupled(
      (
        <<[String],
        <<[Int],
        <<[String],
        <<[Int],
        <<?[java.sql.Timestamp],
        <<?[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal],
        <<[String],
        <<?[String],
        <<?[String]
      )
    )
  }

  /** Table description of table receipts. Objects of this class serve as prototypes for rows in queries. */
  class Receipts(_tableTag: Tag) extends profile.api.Table[ReceiptsRow](_tableTag, Some("ethereum"), "receipts") {
    def * =
      (
        transactionHash,
        transactionIndex,
        blockHash,
        blockLevel,
        timestamp,
        contractAddress,
        cumulativeGasUsed,
        gasUsed,
        logsBloom,
        status,
        root
      ) <> (ReceiptsRow.tupled, ReceiptsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(transactionHash),
          Rep.Some(transactionIndex),
          Rep.Some(blockHash),
          Rep.Some(blockLevel),
          timestamp,
          contractAddress,
          Rep.Some(cumulativeGasUsed),
          Rep.Some(gasUsed),
          Rep.Some(logsBloom),
          status,
          root
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ => ReceiptsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5, _6, _7.get, _8.get, _9.get, _10, _11)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column transaction_hash SqlType(text) */
    val transactionHash: Rep[String] = column[String]("transaction_hash")

    /** Database column transaction_index SqlType(int4) */
    val transactionIndex: Rep[Int] = column[Int]("transaction_index")

    /** Database column block_hash SqlType(text) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column timestamp SqlType(timestamp), Default(None) */
    val timestamp: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("timestamp", O.Default(None))

    /** Database column contract_address SqlType(text), Default(None) */
    val contractAddress: Rep[Option[String]] = column[Option[String]]("contract_address", O.Default(None))

    /** Database column cumulative_gas_used SqlType(numeric) */
    val cumulativeGasUsed: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("cumulative_gas_used")

    /** Database column gas_used SqlType(numeric) */
    val gasUsed: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("gas_used")

    /** Database column logs_bloom SqlType(text) */
    val logsBloom: Rep[String] = column[String]("logs_bloom")

    /** Database column status SqlType(text), Default(None) */
    val status: Rep[Option[String]] = column[Option[String]]("status", O.Default(None))

    /** Database column root SqlType(text), Default(None) */
    val root: Rep[Option[String]] = column[Option[String]]("root", O.Default(None))

    /** Index over (blockLevel) (database name ix_receipts_block_level) */
    val index1 = index("ix_receipts_block_level", blockLevel)

    /** Index over (transactionHash) (database name ix_receipts_hash) */
    val index2 = index("ix_receipts_hash", transactionHash)
  }

  /** Collection-like TableQuery object for table Receipts */
  lazy val Receipts = new TableQuery(tag => new Receipts(tag))

  /** Entity class storing rows of table TokensHistory
    *  @param accountAddress Database column account_address SqlType(text)
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param transactionHash Database column transaction_hash SqlType(text)
    *  @param tokenAddress Database column token_address SqlType(text)
    *  @param value Database column value SqlType(numeric)
    *  @param asof Database column asof SqlType(timestamp) */
  case class TokensHistoryRow(
      accountAddress: String,
      blockHash: String,
      blockLevel: Int,
      transactionHash: String,
      tokenAddress: String,
      value: scala.math.BigDecimal,
      asof: java.sql.Timestamp
  )

  /** GetResult implicit for fetching TokensHistoryRow objects using plain SQL queries */
  implicit def GetResultTokensHistoryRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[scala.math.BigDecimal],
      e3: GR[java.sql.Timestamp]
  ): GR[TokensHistoryRow] = GR { prs =>
    import prs._
    TokensHistoryRow.tupled(
      (<<[String], <<[String], <<[Int], <<[String], <<[String], <<[scala.math.BigDecimal], <<[java.sql.Timestamp])
    )
  }

  /** Table description of table tokens_history. Objects of this class serve as prototypes for rows in queries. */
  class TokensHistory(_tableTag: Tag)
      extends profile.api.Table[TokensHistoryRow](_tableTag, Some("ethereum"), "tokens_history") {
    def * =
      (accountAddress, blockHash, blockLevel, transactionHash, tokenAddress, value, asof) <> (TokensHistoryRow.tupled, TokensHistoryRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(accountAddress),
          Rep.Some(blockHash),
          Rep.Some(blockLevel),
          Rep.Some(transactionHash),
          Rep.Some(tokenAddress),
          Rep.Some(value),
          Rep.Some(asof)
        )
      ).shaped.<>(
        { r =>
          import r._; _1.map(_ => TokensHistoryRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column account_address SqlType(text) */
    val accountAddress: Rep[String] = column[String]("account_address")

    /** Database column block_hash SqlType(text) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column transaction_hash SqlType(text) */
    val transactionHash: Rep[String] = column[String]("transaction_hash")

    /** Database column token_address SqlType(text) */
    val tokenAddress: Rep[String] = column[String]("token_address")

    /** Database column value SqlType(numeric) */
    val value: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("value")

    /** Database column asof SqlType(timestamp) */
    val asof: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("asof")

    /** Index over (accountAddress) (database name ix_tokens_history_account_address) */
    val index1 = index("ix_tokens_history_account_address", accountAddress)

    /** Index over (blockLevel) (database name ix_tokens_history_block_level) */
    val index2 = index("ix_tokens_history_block_level", blockLevel)

    /** Index over (tokenAddress) (database name ix_tokens_history_token_address) */
    val index3 = index("ix_tokens_history_token_address", tokenAddress)
  }

  /** Collection-like TableQuery object for table TokensHistory */
  lazy val TokensHistory = new TableQuery(tag => new TokensHistory(tag))

  /** Entity class storing rows of table TokenTransfers
    *  @param tokenAddress Database column token_address SqlType(text)
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param timestamp Database column timestamp SqlType(timestamp), Default(None)
    *  @param transactionHash Database column transaction_hash SqlType(text)
    *  @param logIndex Database column log_index SqlType(int4)
    *  @param fromAddress Database column from_address SqlType(text)
    *  @param toAddress Database column to_address SqlType(text)
    *  @param value Database column value SqlType(numeric) */
  case class TokenTransfersRow(
      tokenAddress: String,
      blockHash: String,
      blockLevel: Int,
      timestamp: Option[java.sql.Timestamp] = None,
      transactionHash: String,
      logIndex: Int,
      fromAddress: String,
      toAddress: String,
      value: scala.math.BigDecimal
  )

  /** GetResult implicit for fetching TokenTransfersRow objects using plain SQL queries */
  implicit def GetResultTokenTransfersRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[Option[java.sql.Timestamp]],
      e3: GR[scala.math.BigDecimal]
  ): GR[TokenTransfersRow] = GR { prs =>
    import prs._
    TokenTransfersRow.tupled(
      (
        <<[String],
        <<[String],
        <<[Int],
        <<?[java.sql.Timestamp],
        <<[String],
        <<[Int],
        <<[String],
        <<[String],
        <<[scala.math.BigDecimal]
      )
    )
  }

  /** Table description of table token_transfers. Objects of this class serve as prototypes for rows in queries. */
  class TokenTransfers(_tableTag: Tag)
      extends profile.api.Table[TokenTransfersRow](_tableTag, Some("ethereum"), "token_transfers") {
    def * =
      (tokenAddress, blockHash, blockLevel, timestamp, transactionHash, logIndex, fromAddress, toAddress, value) <> (TokenTransfersRow.tupled, TokenTransfersRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(tokenAddress),
          Rep.Some(blockHash),
          Rep.Some(blockLevel),
          timestamp,
          Rep.Some(transactionHash),
          Rep.Some(logIndex),
          Rep.Some(fromAddress),
          Rep.Some(toAddress),
          Rep.Some(value)
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ => TokenTransfersRow.tupled((_1.get, _2.get, _3.get, _4, _5.get, _6.get, _7.get, _8.get, _9.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column token_address SqlType(text) */
    val tokenAddress: Rep[String] = column[String]("token_address")

    /** Database column block_hash SqlType(text) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column timestamp SqlType(timestamp), Default(None) */
    val timestamp: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("timestamp", O.Default(None))

    /** Database column transaction_hash SqlType(text) */
    val transactionHash: Rep[String] = column[String]("transaction_hash")

    /** Database column log_index SqlType(int4) */
    val logIndex: Rep[Int] = column[Int]("log_index")

    /** Database column from_address SqlType(text) */
    val fromAddress: Rep[String] = column[String]("from_address")

    /** Database column to_address SqlType(text) */
    val toAddress: Rep[String] = column[String]("to_address")

    /** Database column value SqlType(numeric) */
    val value: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("value")

    /** Index over (tokenAddress) (database name ix_token_transfers_address) */
    val index1 = index("ix_token_transfers_address", tokenAddress)

    /** Index over (blockLevel) (database name ix_token_transfers_block_level) */
    val index2 = index("ix_token_transfers_block_level", blockLevel)

    /** Index over (fromAddress) (database name ix_token_transfers_from) */
    val index3 = index("ix_token_transfers_from", fromAddress)

    /** Index over (toAddress) (database name ix_token_transfers_to) */
    val index4 = index("ix_token_transfers_to", toAddress)
  }

  /** Collection-like TableQuery object for table TokenTransfers */
  lazy val TokenTransfers = new TableQuery(tag => new TokenTransfers(tag))

  /** Entity class storing rows of table Transactions
    *  @param hash Database column hash SqlType(text), PrimaryKey
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param timestamp Database column timestamp SqlType(timestamp), Default(None)
    *  @param source Database column source SqlType(text)
    *  @param gas Database column gas SqlType(numeric)
    *  @param gasPrice Database column gas_price SqlType(numeric)
    *  @param input Database column input SqlType(text)
    *  @param nonce Database column nonce SqlType(text)
    *  @param destination Database column destination SqlType(text), Default(None)
    *  @param transactionIndex Database column transaction_index SqlType(int4)
    *  @param amount Database column amount SqlType(numeric)
    *  @param v Database column v SqlType(text)
    *  @param r Database column r SqlType(text)
    *  @param s Database column s SqlType(text) */
  case class TransactionsRow(
      hash: String,
      blockHash: String,
      blockLevel: Int,
      timestamp: Option[java.sql.Timestamp] = None,
      source: String,
      gas: scala.math.BigDecimal,
      gasPrice: scala.math.BigDecimal,
      input: String,
      nonce: String,
      destination: Option[String] = None,
      transactionIndex: Int,
      amount: scala.math.BigDecimal,
      v: String,
      r: String,
      s: String
  )

  /** GetResult implicit for fetching TransactionsRow objects using plain SQL queries */
  implicit def GetResultTransactionsRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[Option[java.sql.Timestamp]],
      e3: GR[scala.math.BigDecimal],
      e4: GR[Option[String]]
  ): GR[TransactionsRow] = GR { prs =>
    import prs._
    TransactionsRow.tupled(
      (
        <<[String],
        <<[String],
        <<[Int],
        <<?[java.sql.Timestamp],
        <<[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal],
        <<[String],
        <<[String],
        <<?[String],
        <<[Int],
        <<[scala.math.BigDecimal],
        <<[String],
        <<[String],
        <<[String]
      )
    )
  }

  /** Table description of table transactions. Objects of this class serve as prototypes for rows in queries. */
  class Transactions(_tableTag: Tag)
      extends profile.api.Table[TransactionsRow](_tableTag, Some("ethereum"), "transactions") {
    def * =
      (
        hash,
        blockHash,
        blockLevel,
        timestamp,
        source,
        gas,
        gasPrice,
        input,
        nonce,
        destination,
        transactionIndex,
        amount,
        v,
        r,
        s
      ) <> (TransactionsRow.tupled, TransactionsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(hash),
          Rep.Some(blockHash),
          Rep.Some(blockLevel),
          timestamp,
          Rep.Some(source),
          Rep.Some(gas),
          Rep.Some(gasPrice),
          Rep.Some(input),
          Rep.Some(nonce),
          destination,
          Rep.Some(transactionIndex),
          Rep.Some(amount),
          Rep.Some(v),
          Rep.Some(r),
          Rep.Some(s)
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(
            _ =>
              TransactionsRow.tupled(
                (
                  _1.get,
                  _2.get,
                  _3.get,
                  _4,
                  _5.get,
                  _6.get,
                  _7.get,
                  _8.get,
                  _9.get,
                  _10,
                  _11.get,
                  _12.get,
                  _13.get,
                  _14.get,
                  _15.get
                )
              )
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column hash SqlType(text), PrimaryKey */
    val hash: Rep[String] = column[String]("hash", O.PrimaryKey)

    /** Database column block_hash SqlType(text) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column timestamp SqlType(timestamp), Default(None) */
    val timestamp: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("timestamp", O.Default(None))

    /** Database column source SqlType(text) */
    val source: Rep[String] = column[String]("source")

    /** Database column gas SqlType(numeric) */
    val gas: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("gas")

    /** Database column gas_price SqlType(numeric) */
    val gasPrice: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("gas_price")

    /** Database column input SqlType(text) */
    val input: Rep[String] = column[String]("input")

    /** Database column nonce SqlType(text) */
    val nonce: Rep[String] = column[String]("nonce")

    /** Database column destination SqlType(text), Default(None) */
    val destination: Rep[Option[String]] = column[Option[String]]("destination", O.Default(None))

    /** Database column transaction_index SqlType(int4) */
    val transactionIndex: Rep[Int] = column[Int]("transaction_index")

    /** Database column amount SqlType(numeric) */
    val amount: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("amount")

    /** Database column v SqlType(text) */
    val v: Rep[String] = column[String]("v")

    /** Database column r SqlType(text) */
    val r: Rep[String] = column[String]("r")

    /** Database column s SqlType(text) */
    val s: Rep[String] = column[String]("s")

    /** Foreign key referencing Blocks (database name ethereum_transactions_block_hash_fkey) */
    lazy val blocksFk = foreignKey("ethereum_transactions_block_hash_fkey", blockHash, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )

    /** Index over (blockLevel) (database name ix_transactions_block_level) */
    val index1 = index("ix_transactions_block_level", blockLevel)

    /** Index over (destination) (database name ix_transactions_destination) */
    val index2 = index("ix_transactions_destination", destination)

    /** Index over (source) (database name ix_transactions_source) */
    val index3 = index("ix_transactions_source", source)
  }

  /** Collection-like TableQuery object for table Transactions */
  lazy val Transactions = new TableQuery(tag => new Transactions(tag))
}
