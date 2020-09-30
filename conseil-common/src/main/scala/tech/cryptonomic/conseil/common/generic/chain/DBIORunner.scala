package tech.cryptonomic.conseil.common.generic.chain

import slick.dbio.DBIO

import scala.concurrent.Future

trait DBIORunner {

  /**
    * Runs DBIO action for reading purposes
    * @param  action action to be performed on db
    * @return result of DBIO action as a Future
    */
  def runQuery[A](action: DBIO[A]): Future[A]
}
