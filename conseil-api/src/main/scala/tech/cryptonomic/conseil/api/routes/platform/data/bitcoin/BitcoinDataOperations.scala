package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.common.bitcoin.Tables
import tech.cryptonomic.conseil.common.bitcoin.Tables.{BlocksRow, TransactionsRow}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.BlockHash
import tech.cryptonomic.conseil.common.util.DatabaseUtil

import scala.concurrent.Future

class BitcoinDataOperations extends ApiDataOperations {
  override lazy val dbReadHandle: Database = DatabaseUtil.conseilDb

  /**
    * Fetches the most recent block stored in the database.
    *
    * @return Latest block.
    */
  def fetchLatestBlock(): Future[Option[BlocksRow]] =
    runQuery(Tables.Blocks.sortBy(_.time).take(1).result.headOption)

  /**
    * Fetches a transaction by block hash from the db.
    *
    * @param hash The block's hash
    * @return The transaction, if the hash matches anything
    */
  //TODO Potentially not needed
  def fetchTransaction(hash: BlockHash): Future[Option[TransactionsRow]] =
    runQuery(Tables.Transactions.filter(_.hash === hash.value).result.headOption)

}
