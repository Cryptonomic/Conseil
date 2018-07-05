package tech.cryptonomic.conseil.util

import scala.math.sqrt

object MathUtil {

  /**
    * Average value of a sequence of integers.
    * @param l Sequence of integers.
    * @return
    */
  def mean(l: Seq[Int]): Double =
    l.sum / l.length

  /**
    * Standard deviation of a sequence of integers.
    * @param l Sequence of integers/
    * @return
    */
  def stdev(l: Seq[Int]): Double = {
    val m = mean(l)
    val len = l.length
    sqrt(l.map(x => (x - m)*(x - m)).sum / len)
  }

}
