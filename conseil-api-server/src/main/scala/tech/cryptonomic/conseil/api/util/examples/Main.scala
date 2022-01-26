package tech.cryptonomic.conseil.api.util

import cats.effect.{IO, IOApp}

import scala.concurrent.duration._
import scala.util.Random

import tech.cryptonomic.conseil.api.util.syntax._

object Main extends IOApp.Simple {
  val fails = IO.raiseError[Int](new RuntimeException("random error"))
  val succeeds = IO.pure(42)

  val simulateError = List(fails, succeeds, succeeds, succeeds, fails, fails, succeeds)
    .filter(_ == fails)

  def randIdx =
    for {
      idx <- IO.pure(Random.nextInt(simulateError.size))
      _ <- IO(s"index extracted: $idx").debug
      res <- simulateError(idx)
    } yield res

  def simulate() = IO.sleep(400.millis) >> randIdx

  def run = simulate().retry(10).debug.void
}
