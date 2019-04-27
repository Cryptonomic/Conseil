package tech.cryptonomic.conseil.generic.chain

import slick.dbio.DBIO

import scala.concurrent.Future

trait MetadataOperations {

  /**
    * Runs DBIO action
    * @param  action action to be performed on db
    * @return result of DBIO action as a Future
    */
  def runQuery[A](action: DBIO[A]): Future[A]
}
