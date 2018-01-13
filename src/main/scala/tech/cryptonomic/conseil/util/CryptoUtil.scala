package tech.cryptonomic.conseil.util

import fr.acinq.bitcoin.Base58Check

import scala.util.Try

/**
  * Utility function for common cryptographic operations.
  */
object CryptoUtil {

  def getAsciiForPrefix(prefix: String): Try[List[Byte]] = Try {
    prefix match {
      case "tz1"    => List[Byte](3.toByte, 99.toByte, 29.toByte)
      case "edpk"   => List[Byte](13.toByte, 15.toByte, 37.toByte, 217.toByte)
      case "edsk"   => List[Byte](43.toByte, 246.toByte, 78.toByte, 7.toByte)
      case "edsig"  => List[Byte](9.toByte, 245.toByte, 205.toByte, 134.toByte, 18.toByte)
      case "op"     => List[Byte](5.toByte, 116.toByte)
      case _        => throw new Exception(s"Could not find prefix for $prefix!")
    }
  }

  def base58CheckEncode(payload: List[Byte], prefix: String): Try[String] =
    getAsciiForPrefix(prefix).flatMap { asciiDigits =>
      Try {
        Base58Check.encode(asciiDigits, payload)
      }
    }

  def base58CheckDecode(s: String, prefix: String): Try[Array[Byte]] =
    getAsciiForPrefix(prefix).flatMap{ asciiDigits =>
      Try {
        val charsToSlice = asciiDigits.length
        val decoded = Base58Check.decode(s)
        val decodedBytes = decoded._1  :: decoded._2.data.toList
        decodedBytes.toArray.slice(charsToSlice, decodedBytes.length)
      }
    }
}
