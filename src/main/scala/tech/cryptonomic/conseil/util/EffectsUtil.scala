package tech.cryptonomic.conseil.util

import cats._
import cats.arrow.FunctionK
import cats.effect.IO
import scala.concurrent.Future

object EffectsUtil {

    def toIO[T](future: => Future[T]): IO[T] = IO.fromFuture(IO(future))

    /** a polymorphic value function that converts any future wrapper to an IO */
    implicit val ioFromFuture: Future ~> IO = Lambda[FunctionK[Future, IO]](fut => toIO(fut))

}