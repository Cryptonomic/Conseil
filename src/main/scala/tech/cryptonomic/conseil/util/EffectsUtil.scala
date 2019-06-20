package tech.cryptonomic.conseil.util

import cats._
import cats.arrow.FunctionK
import cats.effect.IO
import scala.concurrent.Future
import slick.dbio.DBIO
import scala.concurrent.ExecutionContext
import cats.effect.ContextShift

object EffectsUtil {

  def toIO[T](future: => Future[T]): IO[T] = IO.fromFuture(IO(future))

  /** Runs the db action converting the result to a lazy IO value */
  def runDbIO[T](db: DatabaseUtil.DatabaseType, dbAction: DBIO[T])(implicit defaultContext: ContextShift[IO]): IO[T] =
    toIO(db.run(dbAction)).guarantee(IO.shift)

  /** A polymorphic value function that converts any future wrapper to an IO
    * that is, it's independent of the type of value in the future, it just converts the "wrapping".
    * It's available for any higher-kinded operation from cats that might need such available "natural transformation".
    * E.g. this is used to convert RpcHandlers based on Future to one that returns IO values.
    */
  implicit val ioFromFuture: Future ~> IO = Lambda[FunctionK[Future, IO]](fut => toIO(fut))

}
