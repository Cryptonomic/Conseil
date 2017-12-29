package tech.cryptonomic.conseil.tezos

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

/**
  * Functionality for fetching data from the Conseil database.
  */
object ApiOperations {

  lazy val dbHandle: Database = DatabaseUtil.db

  /**
    * Fetches the level of the most recent block stored in the database.
    * @return Max level
    */
  def fetchMaxLevel(): Try[Int] = Try {
    val op: Future[Option[Int]] = dbHandle.run(Tables.Blocks
      .map(_.level)
      .max.result)
    val maxLevelOpt = Await.result(op, Duration.Inf)
    maxLevelOpt match {
      case Some(maxLevel) => maxLevel
      case None => throw new Exception("No blocks in the database!")
    }
  }

  /**
    * Fetches the most recent block stored in the database.
    * @return Latest block
    */
  def fetchLatestBlock(): Try[Tables.BlocksRow] =
    fetchMaxLevel().flatMap { maxLevel =>
      Try {
        val op: Future[Seq[tezos.Tables.BlocksRow]] = dbHandle.run(Tables.Blocks
          .filter(_.level === maxLevel)
          .take(1).result)
        Await.result(op, Duration.Inf).head
      }
    }

  /**
    * Fetches all blocks from the db.
    * @return List of blocks
    */
  def fetchBlocks(): Try[Seq[Tables.BlocksRow]] = Try {
    val op = dbHandle.run(Tables.Blocks
      .take(1000).result)
    Await.result(op, Duration.Inf)
  }

  /**
    * Fetches a block by block hash from the db.
    * @param hash   The block's hash
    * @return       Block
    */
  def fetchBlock(hash: String): Try[Tables.BlocksRow] = Try {
    val op = dbHandle.run(Tables.Blocks
      .filter(_.hash === hash)
      .take(1).result)
    Await.result(op, Duration.Inf).head
  }

  /**
    * Fetches   A list of accounts from the db.
    * @return   List of accounts
    */
  def fetchAccounts(): Try[Seq[String]] =
    fetchLatestBlock().flatMap { latestBlock =>
      Try {
        val op = dbHandle.run(Tables.Accounts
          .filter(_.blockId === latestBlock.hash)
          .map(_.accountId).result)
        Await.result(op, Duration.Inf)
      }
    }

  /**
    * Fetches an account by account_id from the db.
    * @param account_id   The account's id number
    * @return             Account
    */
  def fetchAccount(account_id: String): Try[Tables.AccountsRow] =
    fetchLatestBlock().flatMap { latestBlock =>
      Try {
        val op: Future[Seq[tezos.Tables.AccountsRow]] = dbHandle.run(Tables.Accounts
          .filter(_.blockId === latestBlock.hash)
          .filter(_.accountId === account_id)
          .take(1).result)
        Await.result(op, Duration.Inf).head
      }
    }

  /**
    * Gets all transactions linked to an operation group hash.
    * @param operation_group_hash The hash of an operation group
    * @return A list of transaction records
    */
  def getTransactions(operation_group_hash: String): Try[Seq[Tables.TransactionsRow]] =
    Try {
      val op = dbHandle.run(Tables.Transactions
        .filter(_.operationGroupHash === operation_group_hash).result)
      Await.result(op, Duration.Inf)
    }

  /**
    * Gets all endorsements linked to an operation group hash.
    * @param operation_group_hash The hash of an operation group
    * @return A list of endorsement records
    */
  def getEndorsements(operation_group_hash: String): Try[Seq[Tables.EndorsementsRow]] =
    Try {
      val op = dbHandle.run(Tables.Endorsements
        .filter(_.operationGroupHash === operation_group_hash).result)
      Await.result(op, Duration.Inf)
    }

  /**
    * Fetches all operation groups from the database.
    * @return A list of operation groups
    */
  def fetchOperationGroups(): Try[Seq[String]] =
    Try {
      val op = dbHandle.run(Tables.OperationGroups
        .map(_.hash).take(1000).result)
      Await.result(op, Duration.Inf)
    }

  /**
    * Fetches an operation group from the database by its operation group hash.
    * @param  operation_group_hash The hash of an operation group
    * @return A record of an operation group
    */
  def fetchOperationGroup(operation_group_hash: String): Try[Seq[Tables.OperationGroupsRow]] =
    Try {
      val op: Future[Seq[tezos.Tables.OperationGroupsRow]] = dbHandle.run(Tables.OperationGroups
        .filter(_.hash === operation_group_hash)
        .take(1).result)
      Await.result(op, Duration.Inf)
    }

  /**
    * Fetches an operation group's metadata and operations from the db by is operation group hash.
    * @param operation_group_hash The hash of an operation group
    * @return Operation group metadata and operations as a json string
    */
  def fetchOperationGroupWithOperations(operation_group_hash: String): Try[Map[String, Seq[Product with Serializable]]] =
    fetchOperationGroup(operation_group_hash).flatMap {  metadata =>
      getTransactions(operation_group_hash).flatMap { transactions =>
        getEndorsements(operation_group_hash).flatMap { endorsements =>
          Try {
            Map(
              "metadata" -> metadata,
              "transactions" -> transactions,
              "endorsements" -> endorsements
            )
          }
        }
      }
    }

}
