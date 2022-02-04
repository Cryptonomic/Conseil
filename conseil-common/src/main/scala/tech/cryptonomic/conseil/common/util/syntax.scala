package tech.cryptonomic.conseil.common.util

import cats.effect.IO

import scala.concurrent.Future

object syntax {
  implicit class DebugOps[A](io: IO[A]) {
    def debug =
      for {
        a <- io
        t = Thread.currentThread().getName()
        _ = println(s"[$t] $a")
      } yield a
  }

  implicit class FutureOps[A](future: Future[A]) {
    def toIO = IO.fromFuture(IO(future))
  }
}
