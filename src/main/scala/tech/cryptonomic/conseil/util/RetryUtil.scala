package tech.cryptonomic.conseil.util

import com.typesafe.scalalogging.LazyLogging
import scala.annotation.tailrec
import scala.util.{Failure, Try}

object RetryUtil extends LazyLogging {

  @tailrec
  def retry[A](attempts: Int)(fn: => Try[A]): Try[A] = fn match {
    case Failure(_) if attempts > 1 => {
      logger.info("Retry number: " + attempts.toString)
      retry(attempts - 1)(fn)
    }
    case noMoreAttempts => noMoreAttempts
  }

}
