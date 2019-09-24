package tech.cryptonomic.conseil.util

import cats.effect.IO
import slick.dbio.DBIO
import scala.concurrent.Future
import cats.effect.ContextShift
import org.reactivestreams.Publisher
import fs2.interop.reactivestreams._

/** Provides tools to work with and convert to IO operations */
object IOUtil {

  /** Transform any slick action to an IO result which will run the db operation when the IO is executed
    * @param runner a way to execute the database action, i.e. use the database instance to invoke `db.run`
    * @param action the db action to execute
    * @param shifter a necessary context shift to handle threads correctly
    * @tparam T the result of the operation
    */
  def runDbToIO[T](runner: DBIO[T] => Future[T])(action: DBIO[T])(implicit shifter: ContextShift[IO]): IO[T] =
    IO.fromFuture(IO(runner(action))).guarantee(IO.shift)

  /** Transforms a reactive publisher to a purely functional stream over IO.
    * Useful to convert streaming database results based on reactive publisher.
    */
  def publishStream[T](publisher: Publisher[T])(implicit shifter: ContextShift[IO]): fs2.Stream[IO, T] = publisher.toStream[IO]()

}