package tech.cryptonomic.conseil.common.ethereum

import slick.sql.SqlAction

// Views from the database, this file is created manually, because Slick isn't able to generate it in the right way.

/** Stand-alone Slick data model for immediate use */
object Views extends {
  val profile = slick.jdbc.PostgresProfile
} with Views

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Views {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._

  /** Entity class storing rows of view Tokens
    *  @param address Database column address SqlType(text)
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param timestamp Database column timestamp SqlType(timestamp)
    *  @param name Database column name SqlType(text)
    *  @param symbol Database column symbol SqlType(text)
    *  @param decimals Database column decimals SqlType(int4)
    *  @param totalSupply Database column total_supply SqlType(numeric) */
  case class TokensRow(
      address: String,
      blockHash: String,
      blockLevel: Int,
      timestamp: Option[java.sql.Timestamp],
      name: Option[String],
      symbol: Option[String],
      decimals: Option[Int],
      totalSupply: Option[scala.math.BigDecimal]
  )

  /** Table description of view tokens. Objects of this class serve as prototypes for rows in queries. */
  class Tokens(_tableTag: Tag) extends profile.api.Table[TokensRow](_tableTag, Some("ethereum"), "tokens") {
    def * =
      (address, blockHash, blockLevel, timestamp, name, symbol, decimals, totalSupply) <> (TokensRow.tupled, TokensRow.unapply)

    /** Database column address SqlType(text) */
    val address: Rep[String] = column[String]("address")

    /**  Database column block_hash SqlType(text) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("timestamp", O.Default(None))

    /** Database column name SqlType(text) */
    val name: Rep[Option[String]] = column[Option[String]]("name")

    /** Database column symbol SqlType(text) */
    val symbol: Rep[Option[String]] = column[Option[String]]("symbol")

    /** Database column decimals SqlType(int4) */
    val decimals: Rep[Option[Int]] = column[Option[Int]]("decimals")

    /** Database column total_supply SqlType(numeric) */
    val totalSupply: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("total_supply")
  }

  /** Collection-like TableQuery object for view Tokens */
  lazy val Tokens = new TableQuery(tag => new Tokens(tag))

  lazy val TokensViewSql: SqlAction[Int, NoStream, Effect] = sqlu"""
    CREATE OR REPLACE VIEW ethereum.tokens AS
      SELECT
        address,
        block_hash,
        block_level,
        timestamp,
        name,
        symbol,
        decimals,
        total_supply
      FROM
        ethereum.accounts
      WHERE
        token_standard IS NOT NULL
    """

  /** Entity class storing rows of view Contracts
    *  @param address Database column address SqlType(text)
    *  @param blockHash Database column block_hash SqlType(text)
    *  @param blockLevel Database column block_level SqlType(int4)
    *  @param timestamp Database column timestamp SqlType(timestamp)
    *  @param bytecode Database column bytecode SqlType(text)
    *  @param tokenStandard Database column token_standard SqlType(text) */
  case class ContractsRow(
      address: String,
      blockHash: String,
      blockLevel: Int,
      timestamp: Option[java.sql.Timestamp],
      bytecode: Option[String],
      bytecodeHash: Option[String],
      tokenStandard: Option[String]
  )

  /** Table description of view contracts. Objects of this class serve as prototypes for rows in queries. */
  class Contracts(_tableTag: Tag) extends profile.api.Table[ContractsRow](_tableTag, Some("ethereum"), "contracts") {
    def * =
      (address, blockHash, blockLevel, timestamp, bytecode, bytecodeHash, tokenStandard) <> (ContractsRow.tupled, ContractsRow.unapply)

    /** Database column address SqlType(text) */
    val address: Rep[String] = column[String]("address")

    /**  Database column block_hash SqlType(text) */
    val blockHash: Rep[String] = column[String]("block_hash")

    /** Database column block_level SqlType(int4) */
    val blockLevel: Rep[Int] = column[Int]("block_level")

    /** Database column timestamp SqlType(timestamp) */
    val timestamp: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("timestamp", O.Default(None))

    /** Database column bytecode SqlType(text) */
    val bytecode: Rep[Option[String]] = column[Option[String]]("bytecode")

    /** Database column bytecode_hash SqlType(text) */
    val bytecodeHash: Rep[Option[String]] = column[Option[String]]("bytecode_hash")

    /** Database column token_standard SqlType(text) */
    val tokenStandard: Rep[Option[String]] = column[Option[String]]("token_standard")

  }

  /** Collection-like TableQuery object for view Contracts */
  lazy val Contracts = new TableQuery(tag => new Contracts(tag))

  lazy val ContractsViewSql: SqlAction[Int, NoStream, Effect] = sqlu"""
    CREATE OR REPLACE VIEW ethereum.contracts AS
      SELECT
        address,
        block_hash,
        block_level,
        timestamp,
        bytecode,
        bytecode_hash,
        token_standard
      FROM
        ethereum.accounts
      WHERE
        bytecode IS NOT NULL
    """
}
