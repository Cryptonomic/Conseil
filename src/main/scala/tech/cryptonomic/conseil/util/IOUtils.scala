package tech.cryptonomic.conseil.util
import com.typesafe.scalalogging.LazyLogging
import cats.effect.IO
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import cats.effect.ContextShift

object IOUtils {

  /** Mix it in to provide IO logging on top of a com.typesafe.Logger */
  trait IOLogging {
    self: LazyLogging =>

    /* lift side-effecting logging calls into a pure IO context */
    protected def liftLog(impureLogging: Logger => Unit): IO[Unit] =
      IO(impureLogging(logger))

  }

  /** Lifts side-effecting future calls into a pure IO context
    * Take care to not pass an already created future instance, unless it's
    * already fulfilled as a success or failure with a value.
    * Creating an async future computation and passing the reference to this
    * method won't prevent the future from starting, therefore making the use
    * of an IO wrapper useless for passing it along as a pure value.
    * @param future the async computation
    */
  def lift[T](future: => Future[T])(implicit cs: ContextShift[IO]): IO[T] =
    IO.fromFuture(IO(future))

}
