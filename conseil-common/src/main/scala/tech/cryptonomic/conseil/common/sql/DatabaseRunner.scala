package tech.cryptonomic.conseil.common.sql

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.generic.chain.DBIORunner

import scala.concurrent.Future

/** Provides the implementation for `MetadataOperations` trait from database */
trait DatabaseRunner extends DBIORunner {
  def dbReadHandle: Database

  /**
    * @see `MetadataOperations#runQuery`
    */
  override def runQuery[A](action: DBIO[A]): Future[A] =
    dbReadHandle.run(action)
}
