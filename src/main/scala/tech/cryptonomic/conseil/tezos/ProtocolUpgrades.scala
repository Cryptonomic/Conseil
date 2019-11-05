package tech.cryptonomic.conseil.tezos
import slick.dbio.DBIO

object ProtocolUpgrades {

  val protocol005Level = 655361

  /**
  * Trigger to run updates to account balances after protocol 005. (Write a doc)
    * @return Whether or not protocol005 is in effect
    */
  def isProtocol005Active: DBIO[Boolean] =
    TezosDatabaseOperations.queryMaxBlockLevel(x => x >= protocol005Level)

  // DBIO[Account]? AccountIds?
  //Algo, fetch all accounts with zero balance at protocol005Level - 1 from db
  //fetch all accounts with 1microtez Balance at protocol005 (do we even need a diff?)
  //return that list of informatino
  //tezos node calls only? do we even need the db?
  def fetchProtocol005UpdatedAccounts: Any = ???

  // DBIO[Future]
  // given a list of account ids, collect the latest rows, write new rows with new timestamp
  // do account balance update of 0.000001 (what are the units, tez, or microtez)
  //
  def updateAccountBalancesForProtocol005: Any = ???

}
