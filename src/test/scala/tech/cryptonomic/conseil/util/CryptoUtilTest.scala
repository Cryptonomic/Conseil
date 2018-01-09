package tech.cryptonomic.conseil.util

import org.scalatest.{FlatSpec, Matchers}

import scala.util.Try

class CryptoUtilTest extends FlatSpec with Matchers {

  /*"base58encode" should "correctly produce a string" in {
    val arr = List[Byte](1.toByte, 2.toByte, 3.toByte, 4.toByte)
    val encoded = CryptoUtil.base58encode(arr, "tz1")
    println(encoded)
  }*/

  "base58decode" should "correctly decode a string" in {
    val he = List[Byte](72.toByte, 69.toByte)
    val encoded: Try[String] = CryptoUtil.base58encode(he, "tz1")
    val decoded: Try[Array[Byte]] = CryptoUtil.base58decode(encoded.get, "tz1")
    println(s"encoded: $encoded")
    println(s"decoded: $decoded")
  }

}
