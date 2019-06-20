package tech.cryptonomic.conseil.util

import org.scalatest.{FlatSpec, Matchers}

class MathUtilTest extends FlatSpec with Matchers {

  "MathUtilTest" should "correctly calculate the mean of a sequence" in {
      val dataset = List(1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d)
      MathUtil.mean(dataset) should be(5.5)
    }

  "MathUtilTest" should "correctly calculate the population standard deviation of a sequence" in {
      val dataset = List(1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d)
      MathUtil.stdev(dataset) should be(2.8722813232690143)
    }

  "MathUtilTest" should "correctly calculate the population standard deviation of a sequence corrected for using samples" in {
      val dataset = List(1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d)
      MathUtil.sampledStdev(dataset) should be(3.0276503540974917)
    }
}
