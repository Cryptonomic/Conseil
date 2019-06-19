package tech.cryptonomic.conseil.util

import scala.math.{pow, sqrt}

object MathUtil {

  /**
    * Average value of a sequence
    * @param l Sequence of doubles.
    * @return
    */
  def mean(l: Seq[Double]): Double =
    l.sum / l.length

  /**
    * Standard deviation of a sequence
    * @param l Sequence of doubles.
    * @return
    */
  def stdev(l: Seq[Double]): Double = {
    val m = mean(l)
    val len = l.length.toDouble
    sqrt(l.map(x => pow(x - m, 2)).sum / len)
  }

  /**
    * Standard deviation of a sequence corrected for using samples
    * see https://en.wikipedia.org/wiki/Standard_deviation for Bessel correction
    * @param l Sequence of doubles.
    * @return
    */
  def sampledStdev(l: Seq[Double]): Double = {
    val m = mean(l)
    val samples = (l.length - 1).toDouble
    sqrt(l.map(x => pow(x - m, 2)).sum / samples)
  }

}
