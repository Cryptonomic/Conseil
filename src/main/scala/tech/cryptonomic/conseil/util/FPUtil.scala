package tech.cryptonomic.conseil.util

import scala.util.{Success, Try}

object FPUtil {

  def sequence[A](a: List[Try[A]]): Try[List[A]] = {
    a.foldRight[Try[List[A]]](Success(Nil))((x,y) => x.flatMap( z => y.map(w => z :: w)))
  }

}
