package tech.cryptonomic.conseil.util

import org.scalatest.{FlatSpec, Matchers}

class MathUtilTest extends FlatSpec with Matchers {

  "MathUtilTest" should "correctly calculate the mean of a sequence" in {
    val dataset = List(1,2,3,4,5,6,7,8,9,10)
    MathUtil.mean(dataset) should be (5.5)
  }

  "MathUtilTest" should "correctly caculate the population standard deviation of a sequence" in {
    val dataset = List(1,2,3,4,5,6,7,8,9,10)
    MathUtil.stdev(dataset) should be (2.8722813232690143)
  }

}
