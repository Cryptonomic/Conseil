package tech.cryptonomic.conseil.application

import tech.cryptonomic.conseil.util.IOUtils.lift
import scala.concurrent.Future
import scala.util.control.NonFatal
import cats.effect.{ContextShift, IO}
import cats.syntax.all._

/** Provides common operations useful to generic processing of paginated data from the chain */
trait LorreProcessingUtils {

  /** Converts the iterator to a combinator-rich, pure, IO-based stream
    *
    * @param pages lazy iterator of async results
    * @param adaptFetchError converts any error during processing to another, using this function
    * @param cs needed to handle concurrent handling of IO values
    * @return the stream of contents
    */
  protected def streamPages[Content](
      pages: Iterator[Future[Content]],
      adaptFetchError: Throwable => Throwable = identity
  )(implicit cs: ContextShift[IO]): fs2.Stream[IO, Content] =
    fs2.Stream
      .fromIterator[IO](pages.map(lift(_))) //converts the futures to IO values, delaying computation
      .evalMap(identity) //this will unwrap a layer of IO
      .adaptErr {
        case NonFatal(error) => adaptFetchError(error) //use a custom error for common failures
      }

  /** Converts a stream of lists such that the processing chunk
    * has the desired size
    * @param pageSize the size of the output stream chunks
    */
  protected def ensurePageSize[Elem](pageSize: Int): fs2.Pipe[IO, List[Elem], List[Elem]] = {
    import cats.instances.list._
    it =>
      it.foldMonoid.repartition { elems =>
        fs2.Chunk.iterable(elems.grouped(pageSize).toIterable)
      }
  }
}
