package tech.cryptonomic.conseil.util

import java.nio.charset.StandardCharsets
import java.util.Base64

import org.scalatest.{FlatSpec, Matchers}
import scorex.crypto.encode.{Base16, Base58}

import scala.io.Source
import scala.util.Try

class CryptoUtilTest extends FlatSpec with Matchers {

  "base58encode" should "correctly produce a string" in {
    val payload = List[Byte](72.toByte, 69.toByte)
    val encoded = CryptoUtil.base58encode(payload, "tz1").get
    println(encoded)
  }

  "base58decode" should "correctly decode a string" in {
    /*val he = List[Byte](72.toByte, 69.toByte)
    val encoded: Try[String] = CryptoUtil.base58encode(he, "tz1")
    val decoded: Try[Array[Byte]] = CryptoUtil.base58decode(encoded.get, "tz1")
    val foof = Source.fromBytes(decoded.get, "UTF-8").mkString("")
    val poof = decoded.get.toString
    println(s"encoded: ${encoded.get}")
    println(s"decoded: ${decoded.get}")
    println(s"foof: $foof")
    println(s"poof: $foof")*/
    //val decoded: Array[Byte] = CryptoUtil.base58decode("JxF12TrwUP45BMd", "tz1").get
    //val foof = Source.fromBytes(decoded, "UTF-8").mkString("")
    //println(foof)
    val base58CheckEncoded = "2UzUpWXk9ZtJK"
    val decoded = CryptoUtil.base58decode(base58CheckEncoded, "tz1").get
    println(Source.fromBytes(decoded, "UTF-8").mkString(""))
  }

}
