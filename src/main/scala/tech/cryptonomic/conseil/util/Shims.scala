package tech.cryptonomic.conseil.util

import cats.effect.IO
import scala.concurrent.Future
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

/** Contains compatibility layer between different libraries and datatypes */
object Shims {

  /** wraps async, and possibly yet-to-run, computations into an `IO` effect */
  def futureToIO[A](f: => Future[A]): IO[A] = IO.fromFuture(IO(f))

  /** wraps a database action execution within an `IO` effect, meanwhile delaying the execution itself */
  def runToIO[A, DB <: Database](action: DBIO[A])(implicit db: DB): IO[A] = futureToIO(db.run(action))

}