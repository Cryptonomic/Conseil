package tech.cryptonomic.conseil.common.bitcoin

// Views from the database, this file is created manually, because Slick isn't able to generate it in the right way.

/** Stand-alone Slick data model for immediate use */
object Views extends {
  val profile = slick.jdbc.PostgresProfile
} with Views

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Views {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._

  /** Entity class storing rows of view Accounts
    *  @param address Database column address SqlType(text)
    *  @param value Database column value SqlType(numeric) */
  case class AccountsRow(address: String, value: scala.math.BigDecimal)

  /** Table description of view accounts. Objects of this class serve as prototypes for rows in queries. */
  class Accounts(_tableTag: Tag) extends profile.api.Table[AccountsRow](_tableTag, Some("bitcoin"), "accounts") {
    def * = (address, value) <> (AccountsRow.tupled, AccountsRow.unapply)

    /** Database column address SqlType(text) */
    val address: Rep[String] = column[String]("address")

    /** Database column value SqlType(numeric) */
    val value: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("value")
  }

  /** Collection-like TableQuery object for view Accounts */
  lazy val Accounts = new TableQuery(tag => new Accounts(tag))

  lazy val createAccountsViewSql = sqlu"""
    CREATE OR REPLACE VIEW bitcoin.accounts AS
      SELECT
        script_pub_key_addresses AS address,
        SUM(
          CASE WHEN bitcoin.inputs.output_txid IS NULL THEN
            value
          ELSE
            0
          END) AS value
      FROM
        bitcoin.outputs
        LEFT JOIN bitcoin.inputs ON bitcoin.outputs.txid = bitcoin.inputs.output_txid
          AND bitcoin.outputs.n = bitcoin.inputs.v_out
        GROUP BY
          bitcoin.outputs.script_pub_key_addresses
    """
}
