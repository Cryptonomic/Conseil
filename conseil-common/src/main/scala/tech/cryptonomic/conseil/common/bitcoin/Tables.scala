package tech.cryptonomic.conseil.common.bitcoin
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
  lazy val schema: profile.SchemaDescription = Blocks.schema ++ Inputs.schema ++ Outputs.schema ++ Transactions.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Blocks
    *  @param hash Database column hash SqlType(text), PrimaryKey
    *  @param size Database column size SqlType(int4)
    *  @param strippedSize Database column stripped_size SqlType(int4)
    *  @param weight Database column weight SqlType(int4)
    *  @param height Database column height SqlType(int4)
    *  @param version Database column version SqlType(int4)
    *  @param versionHex Database column version_hex SqlType(text)
    *  @param merkleRoot Database column merkle_root SqlType(text)
    *  @param nonce Database column nonce SqlType(int8)
    *  @param bits Database column bits SqlType(text)
    *  @param difficulty Database column difficulty SqlType(numeric)
    *  @param chainWork Database column chain_work SqlType(text)
    *  @param nTx Database column n_tx SqlType(int4)
    *  @param previousBlockHash Database column previous_block_hash SqlType(text), Default(None)
    *  @param nextBlockHash Database column next_block_hash SqlType(text), Default(None)
    *  @param medianTime Database column median_time SqlType(timestamp)
    *  @param time Database column time SqlType(timestamp) */
  case class BlocksRow(
      hash: String,
      size: Int,
      strippedSize: Int,
      weight: Int,
      height: Int,
      version: Int,
      versionHex: String,
      merkleRoot: String,
      nonce: Long,
      bits: String,
      difficulty: scala.math.BigDecimal,
      chainWork: String,
      nTx: Int,
      previousBlockHash: Option[String] = None,
      nextBlockHash: Option[String] = None,
      medianTime: java.sql.Timestamp,
      time: java.sql.Timestamp
  )

  /** GetResult implicit for fetching BlocksRow objects using plain SQL queries */
  implicit def GetResultBlocksRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[Long],
      e3: GR[scala.math.BigDecimal],
      e4: GR[Option[String]],
      e5: GR[java.sql.Timestamp]
  ): GR[BlocksRow] = GR { prs =>
    import prs._
    BlocksRow.tupled(
      (
        <<[String],
        <<[Int],
        <<[Int],
        <<[Int],
        <<[Int],
        <<[Int],
        <<[String],
        <<[String],
        <<[Long],
        <<[String],
        <<[scala.math.BigDecimal],
        <<[String],
        <<[Int],
        <<?[String],
        <<?[String],
        <<[java.sql.Timestamp],
        <<[java.sql.Timestamp]
      )
    )
  }

  /** Table description of table blocks. Objects of this class serve as prototypes for rows in queries. */
  class Blocks(_tableTag: Tag) extends profile.api.Table[BlocksRow](_tableTag, Some("bitcoin"), "blocks") {
    def * =
      (
        hash,
        size,
        strippedSize,
        weight,
        height,
        version,
        versionHex,
        merkleRoot,
        nonce,
        bits,
        difficulty,
        chainWork,
        nTx,
        previousBlockHash,
        nextBlockHash,
        medianTime,
        time
      ) <> (BlocksRow.tupled, BlocksRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(hash),
          Rep.Some(size),
          Rep.Some(strippedSize),
          Rep.Some(weight),
          Rep.Some(height),
          Rep.Some(version),
          Rep.Some(versionHex),
          Rep.Some(merkleRoot),
          Rep.Some(nonce),
          Rep.Some(bits),
          Rep.Some(difficulty),
          Rep.Some(chainWork),
          Rep.Some(nTx),
          previousBlockHash,
          nextBlockHash,
          Rep.Some(medianTime),
          Rep.Some(time)
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
                  _11.get,
                  _12.get,
                  _13.get,
                  _14,
                  _15,
                  _16.get,
                  _17.get
                )
              )
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column hash SqlType(text), PrimaryKey */
    val hash: Rep[String] = column[String]("hash", O.PrimaryKey)

    /** Database column size SqlType(int4) */
    val size: Rep[Int] = column[Int]("size")

    /** Database column stripped_size SqlType(int4) */
    val strippedSize: Rep[Int] = column[Int]("stripped_size")

    /** Database column weight SqlType(int4) */
    val weight: Rep[Int] = column[Int]("weight")

    /** Database column height SqlType(int4) */
    val height: Rep[Int] = column[Int]("height")

    /** Database column version SqlType(int4) */
    val version: Rep[Int] = column[Int]("version")

    /** Database column version_hex SqlType(text) */
    val versionHex: Rep[String] = column[String]("version_hex")

    /** Database column merkle_root SqlType(text) */
    val merkleRoot: Rep[String] = column[String]("merkle_root")

    /** Database column nonce SqlType(int8) */
    val nonce: Rep[Long] = column[Long]("nonce")

    /** Database column bits SqlType(text) */
    val bits: Rep[String] = column[String]("bits")

    /** Database column difficulty SqlType(numeric) */
    val difficulty: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("difficulty")

    /** Database column chain_work SqlType(text) */
    val chainWork: Rep[String] = column[String]("chain_work")

    /** Database column n_tx SqlType(int4) */
    val nTx: Rep[Int] = column[Int]("n_tx")

    /** Database column previous_block_hash SqlType(text), Default(None) */
    val previousBlockHash: Rep[Option[String]] = column[Option[String]]("previous_block_hash", O.Default(None))

    /** Database column next_block_hash SqlType(text), Default(None) */
    val nextBlockHash: Rep[Option[String]] = column[Option[String]]("next_block_hash", O.Default(None))

    /** Database column median_time SqlType(timestamp) */
    val medianTime: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("median_time")

    /** Database column time SqlType(timestamp) */
    val time: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("time")
  }

  /** Collection-like TableQuery object for table Blocks */
  lazy val Blocks = new TableQuery(tag => new Blocks(tag))

  /** Entity class storing rows of table Inputs
    *  @param txid Database column txid SqlType(text)
    *  @param vOut Database column v_out SqlType(int4), Default(None)
    *  @param scriptSigAsm Database column script_sig_asm SqlType(text), Default(None)
    *  @param scriptSigHex Database column script_sig_hex SqlType(text), Default(None)
    *  @param sequence Database column sequence SqlType(int8)
    *  @param coinbase Database column coinbase SqlType(text), Default(None)
    *  @param txInWitness Database column tx_in_witness SqlType(text), Default(None) */
  case class InputsRow(
      txid: String,
      vOut: Option[Int] = None,
      scriptSigAsm: Option[String] = None,
      scriptSigHex: Option[String] = None,
      sequence: Long,
      coinbase: Option[String] = None,
      txInWitness: Option[String] = None
  )

  /** GetResult implicit for fetching InputsRow objects using plain SQL queries */
  implicit def GetResultInputsRow(
      implicit e0: GR[String],
      e1: GR[Option[Int]],
      e2: GR[Option[String]],
      e3: GR[Long]
  ): GR[InputsRow] = GR { prs =>
    import prs._
    InputsRow.tupled((<<[String], <<?[Int], <<?[String], <<?[String], <<[Long], <<?[String], <<?[String]))
  }

  /** Table description of table inputs. Objects of this class serve as prototypes for rows in queries. */
  class Inputs(_tableTag: Tag) extends profile.api.Table[InputsRow](_tableTag, Some("bitcoin"), "inputs") {
    def * =
      (txid, vOut, scriptSigAsm, scriptSigHex, sequence, coinbase, txInWitness) <> (InputsRow.tupled, InputsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      ((Rep.Some(txid), vOut, scriptSigAsm, scriptSigHex, Rep.Some(sequence), coinbase, txInWitness)).shaped.<>({ r =>
        import r._; _1.map(_ => InputsRow.tupled((_1.get, _2, _3, _4, _5.get, _6, _7)))
      }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column txid SqlType(text) */
    val txid: Rep[String] = column[String]("txid")

    /** Database column v_out SqlType(int4), Default(None) */
    val vOut: Rep[Option[Int]] = column[Option[Int]]("v_out", O.Default(None))

    /** Database column script_sig_asm SqlType(text), Default(None) */
    val scriptSigAsm: Rep[Option[String]] = column[Option[String]]("script_sig_asm", O.Default(None))

    /** Database column script_sig_hex SqlType(text), Default(None) */
    val scriptSigHex: Rep[Option[String]] = column[Option[String]]("script_sig_hex", O.Default(None))

    /** Database column sequence SqlType(int8) */
    val sequence: Rep[Long] = column[Long]("sequence")

    /** Database column coinbase SqlType(text), Default(None) */
    val coinbase: Rep[Option[String]] = column[Option[String]]("coinbase", O.Default(None))

    /** Database column tx_in_witness SqlType(text), Default(None) */
    val txInWitness: Rep[Option[String]] = column[Option[String]]("tx_in_witness", O.Default(None))

    /** Foreign key referencing Transactions (database name bitcoin_inputs_txid_fkey) */
    lazy val transactionsFk = foreignKey("bitcoin_inputs_txid_fkey", txid, Transactions)(
      r => r.txid,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )
  }

  /** Collection-like TableQuery object for table Inputs */
  lazy val Inputs = new TableQuery(tag => new Inputs(tag))

  /** Entity class storing rows of table Outputs
    *  @param txid Database column txid SqlType(text)
    *  @param value Database column value SqlType(numeric), Default(None)
    *  @param n Database column n SqlType(int4)
    *  @param scriptPubKeyAsm Database column script_pub_key_asm SqlType(text)
    *  @param scriptPubKeyHex Database column script_pub_key_hex SqlType(text)
    *  @param scriptPubKeyReqSigs Database column script_pub_key_req_sigs SqlType(int4), Default(None)
    *  @param scriptPubKeyType Database column script_pub_key_type SqlType(text)
    *  @param scriptPubKeyAddresses Database column script_pub_key_addresses SqlType(text), Default(None) */
  case class OutputsRow(
      txid: String,
      value: Option[scala.math.BigDecimal] = None,
      n: Int,
      scriptPubKeyAsm: String,
      scriptPubKeyHex: String,
      scriptPubKeyReqSigs: Option[Int] = None,
      scriptPubKeyType: String,
      scriptPubKeyAddresses: Option[String] = None
  )

  /** GetResult implicit for fetching OutputsRow objects using plain SQL queries */
  implicit def GetResultOutputsRow(
      implicit e0: GR[String],
      e1: GR[Option[scala.math.BigDecimal]],
      e2: GR[Int],
      e3: GR[Option[Int]],
      e4: GR[Option[String]]
  ): GR[OutputsRow] = GR { prs =>
    import prs._
    OutputsRow.tupled(
      (<<[String], <<?[scala.math.BigDecimal], <<[Int], <<[String], <<[String], <<?[Int], <<[String], <<?[String])
    )
  }

  /** Table description of table outputs. Objects of this class serve as prototypes for rows in queries. */
  class Outputs(_tableTag: Tag) extends profile.api.Table[OutputsRow](_tableTag, Some("bitcoin"), "outputs") {
    def * =
      (txid, value, n, scriptPubKeyAsm, scriptPubKeyHex, scriptPubKeyReqSigs, scriptPubKeyType, scriptPubKeyAddresses) <> (OutputsRow.tupled, OutputsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(txid),
          value,
          Rep.Some(n),
          Rep.Some(scriptPubKeyAsm),
          Rep.Some(scriptPubKeyHex),
          scriptPubKeyReqSigs,
          Rep.Some(scriptPubKeyType),
          scriptPubKeyAddresses
        )
      ).shaped.<>(
        { r =>
          import r._; _1.map(_ => OutputsRow.tupled((_1.get, _2, _3.get, _4.get, _5.get, _6, _7.get, _8)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column txid SqlType(text) */
    val txid: Rep[String] = column[String]("txid")

    /** Database column value SqlType(numeric), Default(None) */
    val value: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("value", O.Default(None))

    /** Database column n SqlType(int4) */
    val n: Rep[Int] = column[Int]("n")

    /** Database column script_pub_key_asm SqlType(text) */
    val scriptPubKeyAsm: Rep[String] = column[String]("script_pub_key_asm")

    /** Database column script_pub_key_hex SqlType(text) */
    val scriptPubKeyHex: Rep[String] = column[String]("script_pub_key_hex")

    /** Database column script_pub_key_req_sigs SqlType(int4), Default(None) */
    val scriptPubKeyReqSigs: Rep[Option[Int]] = column[Option[Int]]("script_pub_key_req_sigs", O.Default(None))

    /** Database column script_pub_key_type SqlType(text) */
    val scriptPubKeyType: Rep[String] = column[String]("script_pub_key_type")

    /** Database column script_pub_key_addresses SqlType(text), Default(None) */
    val scriptPubKeyAddresses: Rep[Option[String]] = column[Option[String]]("script_pub_key_addresses", O.Default(None))

    /** Foreign key referencing Transactions (database name bitcoin_outputs_txid_fkey) */
    lazy val transactionsFk = foreignKey("bitcoin_outputs_txid_fkey", txid, Transactions)(
      r => r.txid,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )
  }

  /** Collection-like TableQuery object for table Outputs */
  lazy val Outputs = new TableQuery(tag => new Outputs(tag))

  /** Entity class storing rows of table Transactions
    *  @param txid Database column txid SqlType(text), PrimaryKey
    *  @param blockhash Database column blockhash SqlType(text)
    *  @param hash Database column hash SqlType(text)
    *  @param hex Database column hex SqlType(text)
    *  @param size Database column size SqlType(int4)
    *  @param vsize Database column vsize SqlType(int4)
    *  @param weight Database column weight SqlType(int4)
    *  @param version Database column version SqlType(int4)
    *  @param lockTime Database column lock_time SqlType(timestamp)
    *  @param blockTime Database column block_time SqlType(timestamp)
    *  @param time Database column time SqlType(timestamp) */
  case class TransactionsRow(
      txid: String,
      blockhash: String,
      hash: String,
      hex: String,
      size: Int,
      vsize: Int,
      weight: Int,
      version: Int,
      lockTime: java.sql.Timestamp,
      blockTime: java.sql.Timestamp,
      time: java.sql.Timestamp
  )

  /** GetResult implicit for fetching TransactionsRow objects using plain SQL queries */
  implicit def GetResultTransactionsRow(
      implicit e0: GR[String],
      e1: GR[Int],
      e2: GR[java.sql.Timestamp]
  ): GR[TransactionsRow] = GR { prs =>
    import prs._
    TransactionsRow.tupled(
      (
        <<[String],
        <<[String],
        <<[String],
        <<[String],
        <<[Int],
        <<[Int],
        <<[Int],
        <<[Int],
        <<[java.sql.Timestamp],
        <<[java.sql.Timestamp],
        <<[java.sql.Timestamp]
      )
    )
  }

  /** Table description of table transactions. Objects of this class serve as prototypes for rows in queries. */
  class Transactions(_tableTag: Tag)
      extends profile.api.Table[TransactionsRow](_tableTag, Some("bitcoin"), "transactions") {
    def * =
      (txid, blockhash, hash, hex, size, vsize, weight, version, lockTime, blockTime, time) <> (TransactionsRow.tupled, TransactionsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(txid),
          Rep.Some(blockhash),
          Rep.Some(hash),
          Rep.Some(hex),
          Rep.Some(size),
          Rep.Some(vsize),
          Rep.Some(weight),
          Rep.Some(version),
          Rep.Some(lockTime),
          Rep.Some(blockTime),
          Rep.Some(time)
        )
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(
            _ =>
              TransactionsRow
                .tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get))
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column txid SqlType(text), PrimaryKey */
    val txid: Rep[String] = column[String]("txid", O.PrimaryKey)

    /** Database column blockhash SqlType(text) */
    val blockhash: Rep[String] = column[String]("blockhash")

    /** Database column hash SqlType(text) */
    val hash: Rep[String] = column[String]("hash")

    /** Database column hex SqlType(text) */
    val hex: Rep[String] = column[String]("hex")

    /** Database column size SqlType(int4) */
    val size: Rep[Int] = column[Int]("size")

    /** Database column vsize SqlType(int4) */
    val vsize: Rep[Int] = column[Int]("vsize")

    /** Database column weight SqlType(int4) */
    val weight: Rep[Int] = column[Int]("weight")

    /** Database column version SqlType(int4) */
    val version: Rep[Int] = column[Int]("version")

    /** Database column lock_time SqlType(timestamp) */
    val lockTime: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("lock_time")

    /** Database column block_time SqlType(timestamp) */
    val blockTime: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("block_time")

    /** Database column time SqlType(timestamp) */
    val time: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("time")

    /** Foreign key referencing Blocks (database name bitcoin_transactions_blockhash_fkey) */
    lazy val blocksFk = foreignKey("bitcoin_transactions_blockhash_fkey", blockhash, Blocks)(
      r => r.hash,
      onUpdate = ForeignKeyAction.NoAction,
      onDelete = ForeignKeyAction.NoAction
    )
  }

  /** Collection-like TableQuery object for table Transactions */
  lazy val Transactions = new TableQuery(tag => new Transactions(tag))
}
