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
  lazy val schema: profile.SchemaDescription = Blocks.schema ++ Logs.schema ++ Transactions.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Blocks
    *  @param hash Database column hash SqlType(text), PrimaryKey
    *  @param number Database column number SqlType(int4)
    *  @param difficulty Database column difficulty SqlType(text)
    *  @param extraData Database column extra_data SqlType(text)
    *  @param gasLimit Database column gas_limit SqlType(text)
    *  @param gasUsed Database column gas_used SqlType(text)
    *  @param logsBloom Database column logs_bloom SqlType(text)
    *  @param miner Database column miner SqlType(text)
    *  @param mixHash Database column mix_hash SqlType(text)
    *  @param nonce Database column nonce SqlType(text)
    *  @param parentHash Database column parent_hash SqlType(text), Default(None)
    *  @param receiptsRoot Database column receipts_root SqlType(text)
    *  @param sha3Uncles Database column sha3_uncles SqlType(text)
    *  @param size Database column size SqlType(text)
    *  @param stateRoot Database column state_root SqlType(text)
    *  @param totalDifficulty Database column total_difficulty SqlType(text)
    *  @param transactionsRoot Database column transactions_root SqlType(text)
    *  @param uncles Database column uncles SqlType(text), Default(None)
    *  @param timestamp Database column timestamp SqlType(timestamp) */
  case class BlocksRow(
      hash: String,
      number: Int,
      difficulty: String,
      extraData: String,
      gasLimit: String,
      gasUsed: String,
      logsBloom: String,
      miner: String,
      mixHash: String,
      nonce: String,
      parentHash: Option[String] = None,
      receiptsRoot: String,
      sha3Uncles: String,
      size: String,
      stateRoot: String,
      totalDifficulty: String,
      transactionsRoot: String,
      uncles: Option[String] = None,
      timestamp: java.sql.Timestamp
  )

  /** GetResult implicit for fetching BlocksRow objects using plain SQL queries */
  implicit def GetResultBlocksRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[Option[String]],
      e3: GR[java.sql.Timestamp]
  ): GR[BlocksRow] = GR { prs =>
    import prs._
    BlocksRow.tupled(
      (
        <<[String],
        <<[Int],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<?[String],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
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
        number,
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
          Rep.Some(number),
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

    /** Database column number SqlType(int4) */
    val number: Rep[Int] = column[Int]("number")

    /** Database column difficulty SqlType(text) */
    val difficulty: Rep[String] = column[String]("difficulty")

    /** Database column extra_data SqlType(text) */
    val extraData: Rep[String] = column[String]("extra_data")

    /** Database column gas_limit SqlType(text) */
    val gasLimit: Rep[String] = column[String]("gas_limit")

    /** Database column gas_used SqlType(text) */
    val gasUsed: Rep[String] = column[String]("gas_used")

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

    /** Database column size SqlType(text) */
    val size: Rep[String] = column[String]("size")

    /** Database column state_root SqlType(text) */
    val stateRoot: Rep[String] = column[String]("state_root")

    /** Database column total_difficulty SqlType(text) */
    val totalDifficulty: Rep[String] = column[String]("total_difficulty")

    /** Database column transactions_root SqlType(text) */
    val transactionsRoot: Rep[String] = column[String]("transactions_root")

    /** Database column uncles SqlType(text), Default(None) */
    val uncles: Rep[Option[String]] = column[Option[String]]("uncles", O.Default(None))

    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("timestamp")
  }

  /** Collection-like TableQuery object for table Blocks */
  lazy val Blocks = new TableQuery(tag => new Blocks(tag))

  /** Entity class storing rows of table Logs
    *  @param address Database column address SqlType(text), PrimaryKey
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockNumber Database column block_number SqlType(int4)
    *  @param data Database column data SqlType(text)
    *  @param logIndex Database column log_index SqlType(text)
    *  @param removed Database column removed SqlType(bool)
    *  @param topics Database column topics SqlType(text)
    *  @param transactionHash Database column transaction_hash SqlType(text)
    *  @param transactionIndex Database column transaction_index SqlType(text) */
  case class LogsRow(
      address: String,
      blockHash: String,
      blockNumber: Int,
      data: String,
      logIndex: String,
      removed: Boolean,
      topics: String,
      transactionHash: String,
      transactionIndex: String
  )

  /** GetResult implicit for fetching LogsRow objects using plain SQL queries */
  implicit def GetResultLogsRow(implicit e0: GR[String], e1: GR[Int], e2: GR[Boolean]): GR[LogsRow] = GR { prs =>
    import prs._
    LogsRow.tupled(
      (<<[String], <<[String], <<[Int], <<[String], <<[String], <<[Boolean], <<[String], <<[String], <<[String])
    )
  }

  /** Table description of table logs. Objects of this class serve as prototypes for rows in queries. */
  class Logs(_tableTag: Tag) extends profile.api.Table[LogsRow](_tableTag, Some("ethereum"), "logs") {
    def * =
      (address, blockHash, blockNumber, data, logIndex, removed, topics, transactionHash, transactionIndex) <> (LogsRow.tupled, LogsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(address),
          Rep.Some(blockHash),
          Rep.Some(blockNumber),
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
          _1.map(_ => LogsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column address SqlType(text), PrimaryKey */
    val address: Rep[String] = column[String]("address", O.PrimaryKey)

    /** Database column block_hash SqlType(text) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_number SqlType(int4) */
    val blockNumber: Rep[Int] = column[Int]("block_number")

    /** Database column data SqlType(text) */
    val data: Rep[String] = column[String]("data")

    /** Database column log_index SqlType(text) */
    val logIndex: Rep[String] = column[String]("log_index")

    /** Database column removed SqlType(bool) */
    val removed: Rep[Boolean] = column[Boolean]("removed")

    /** Database column topics SqlType(text) */
    val topics: Rep[String] = column[String]("topics")

    /** Database column transaction_hash SqlType(text) */
    val transactionHash: Rep[String] = column[String]("transaction_hash")

    /** Database column transaction_index SqlType(text) */
    val transactionIndex: Rep[String] = column[String]("transaction_index")

    /** Foreign key referencing Blocks (database name ethereum_logs_block_hash_fkey) */
    lazy val blocksFk = foreignKey("ethereum_logs_block_hash_fkey", blockHash, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )
  }

  /** Collection-like TableQuery object for table Logs */
  lazy val Logs = new TableQuery(tag => new Logs(tag))

  /** Entity class storing rows of table Transactions
    *  @param hash Database column hash SqlType(text), PrimaryKey
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockNumber Database column block_number SqlType(int4)
    *  @param from Database column from SqlType(text)
    *  @param gas Database column gas SqlType(text)
    *  @param gasPrice Database column gas_price SqlType(text)
    *  @param input Database column input SqlType(text)
    *  @param nonce Database column nonce SqlType(text)
    *  @param to Database column to SqlType(text)
    *  @param transactionIndex Database column transaction_index SqlType(text)
    *  @param value Database column value SqlType(text)
    *  @param v Database column v SqlType(text)
    *  @param r Database column r SqlType(text)
    *  @param s Database column s SqlType(text) */
  case class TransactionsRow(
      hash: String,
      blockHash: String,
      blockNumber: Int,
      from: String,
      gas: String,
      gasPrice: String,
      input: String,
      nonce: String,
      to: String,
      transactionIndex: String,
      value: String,
      v: String,
      r: String,
      s: String
  )

  /** GetResult implicit for fetching TransactionsRow objects using plain SQL queries */
  implicit def GetResultTransactionsRow(implicit e0: GR[String], e1: GR[Int]): GR[TransactionsRow] = GR { prs =>
    import prs._
    TransactionsRow.tupled(
      (
        <<[String],
        <<[String],
        <<[Int],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<[String],
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
      (hash, blockHash, blockNumber, from, gas, gasPrice, input, nonce, to, transactionIndex, value, v, r, s) <> (TransactionsRow.tupled, TransactionsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(hash),
          Rep.Some(blockHash),
          Rep.Some(blockNumber),
          Rep.Some(from),
          Rep.Some(gas),
          Rep.Some(gasPrice),
          Rep.Some(input),
          Rep.Some(nonce),
          Rep.Some(to),
          Rep.Some(transactionIndex),
          Rep.Some(value),
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
                  _4.get,
                  _5.get,
                  _6.get,
                  _7.get,
                  _8.get,
                  _9.get,
                  _10.get,
                  _11.get,
                  _12.get,
                  _13.get,
                  _14.get
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

    /** Database column block_number SqlType(int4) */
    val blockNumber: Rep[Int] = column[Int]("block_number")

    /** Database column from SqlType(text) */
    val from: Rep[String] = column[String]("from")

    /** Database column gas SqlType(text) */
    val gas: Rep[String] = column[String]("gas")

    /** Database column gas_price SqlType(text) */
    val gasPrice: Rep[String] = column[String]("gas_price")

    /** Database column input SqlType(text) */
    val input: Rep[String] = column[String]("input")

    /** Database column nonce SqlType(text) */
    val nonce: Rep[String] = column[String]("nonce")

    /** Database column to SqlType(text) */
    val to: Rep[String] = column[String]("to")

    /** Database column transaction_index SqlType(text) */
    val transactionIndex: Rep[String] = column[String]("transaction_index")

    /** Database column value SqlType(text) */
    val value: Rep[String] = column[String]("value")

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
  }

  /** Collection-like TableQuery object for table Transactions */
  lazy val Transactions = new TableQuery(tag => new Transactions(tag))
}
