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

  def runDbIO[T](db: DatabaseUtil.DatabaseType, blockingExecutionPool: ExecutionContext)(dbAction: DBIO[T])(implicit defaultContext: ContextShift[IO]): IO[T] =
    for {
      _ <- IO.shift(blockingExecutionPool)
      result <- toIO(db.run(dbAction))
      _ <- IO.shift
    } yield result

  /** A polymorphic value function that converts any future wrapper to an IO
    * that is, it's independent of the type of value in the future, it just converts the "wrapping".
    * It's available for any higher-kinded operation from cats that might need such available "natural transformation".
    * E.g. this is used to convert RpcHandlers based on Future to one that returns IO values.
    */
  implicit val ioFromFuture: Future ~> IO = Lambda[FunctionK[Future, IO]](fut => toIO(fut))

}