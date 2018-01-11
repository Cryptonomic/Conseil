package tech.cryptonomic.conseil.util

import fr.acinq.bitcoin.{Base58, Base58Check, BinaryData}

import scala.io.Source
import scala.util.Try

/**
  * Utility function for common cryptographic operations.
  */
object CryptoUtil {

  def getAsciiForPrefix(prefix: String): Try[List[Byte]] = Try {
    prefix match {
      case "tz1"    => List[Byte](116.toByte, 122.toByte, 49.toByte)
      case "edpk"   => List[Byte](101.toByte, 100.toByte, 112.toByte, 107.toByte)
      case "edsk"   => List[Byte](101.toByte, 100.toByte, 115.toByte, 107.toByte)
      case "edsig"  => List[Byte](101.toByte, 100.toByte, 115.toByte, 105.toByte, 103.toByte)
      case "op"     => List[Byte](111.toByte, 112.toByte)
      case _        => throw new Exception(s"Could not find prefix for $prefix!")
    }
  }

  def base58encode(payload: List[Byte], prefix: String): Try[String] =
    getAsciiForPrefix(prefix).flatMap { asciiDigits =>
      Try {
        Base58Check.encode(asciiDigits, payload)
      }
    }

  def base58decode(s: String, prefix: String): Try[Array[Byte]] =
    getAsciiForPrefix(prefix).flatMap{ asciiDigits =>
      Try {
        val charsToSlice = asciiDigits.length
        val decoded = Base58Check.decode(s)
        val decodedBytes = decoded._1  :: decoded._2.data.toList
        decodedBytes.toArray.slice(charsToSlice, decodedBytes.length)
      }
    }
}
