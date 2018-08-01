package tech.cryptonomic.conseil.util

import scala.util.{Success, Try}
import scala.List._


object FPUtil {

  def map2[A, B, C](a: Try[A], b: Try[C], f: (A,B) => C): Try[C] = {
    a.flatMap( x => b.map(y => f(x, y)))
  }

  def sequence[A](a: List[Try[A]]): Try[List[A]] = {
    //a.foldRight[Option[List[A]]](Some(Nil))((x,y) => map2(x,y){ (z, w) => z :: w)}
    ???
  }

}
