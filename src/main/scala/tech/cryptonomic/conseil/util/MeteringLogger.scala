package tech.cryptonomic.conseil.util
import scala.concurrent.duration.{Duration, NANOSECONDS}
import cats.{Monad, Id}
import cats.implicits._
import cats.effect.IO
import scala.concurrent.ExecutionContext
import cats.effect.Clock

/** Implements metering without wrapping effects, therefore breaking referential transparency */
class UnsafeMeteringLogger(log: String => Unit) extends MeteringLogger[Id](log) {
  override def currentMonotoneTime: Long = System.nanoTime()
}

/** Implements metering using IO */
class IOMeteringLogger(log: String => IO[Unit])(implicit ec: ExecutionContext) extends MeteringLogger[IO](log) {
  private val clock = Clock.extractFromTimer(IO.timer(ec))

  override def currentMonotoneTime: IO[Long] = clock.monotonic(NANOSECONDS)
}

/** Utility class to add time metering of code blocks and logging the details
  * @param log is the function that actually does the logging
  */
  abstract class MeteringLogger[Eff[_] : Monad](log: String => Eff[Unit]) {

  def currentMonotoneTime: Eff[Long]

  /** Wraps some code block to log the time taken to execute, in milliseconds
    * @param logDescription used to identify what's being measured in the log prints
    * @param block the code to measure
    * @return the result of execuding the wrapped code
    */
  def metered[T](logDescription: => String)(block: => Eff[T]): Eff[T] = for {
    start <- currentMonotoneTime
    result <- block
    end <- currentMonotoneTime
    lapse = Duration(end - start, NANOSECONDS).toMillis
    _ <- log(s"$logDescription took $lapse milliseconds")
  } yield result
}
