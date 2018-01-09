package tech.cryptonomic.conseil.util

import fr.acinq.bitcoin.{Base58, Base58Check, BinaryData}

import scala.util.Try

/**
  * Utility function for common cryptographic operations.
  */
object CryptoUtil {

  def getPrefixDigits(prefix: String): Try[List[Byte]] = Try {
    prefix match {
      //case "tz1"    => List[Byte](6.toByte, 161.toByte, 159.toByte)
      case "tz1"    => List[Byte](45.toByte, 51.toByte, 53.toByte)
      case "edpk"   => List[Byte](13.toByte, 15.toByte, 37.toByte, 217.toByte)
      case "edsk"   => List[Byte](43.toByte, 246.toByte, 78.toByte, 7.toByte)
      case "edsig"  => List[Byte](9.toByte, 245.toByte, 205.toByte, 134.toByte, 18.toByte)
      case "o"      => List[Byte](5.toByte, 116.toByte)
      case _        => throw new Exception(s"Could not find prefix for $prefix!")
    }
  }

  def base58encode(payload: List[Byte], prefix: String): Try[String] =
    getPrefixDigits(prefix).flatMap { digits =>
      Try {
        val paylodWithPrefix: List[Byte] = digits ++ payload
        //Base58.encode(paylodWithPrefix)
        Base58Check.encode(digits, payload)
      }
    }

  def base58decode(s: String, prefix: String): Try[Array[Byte]] =
    getPrefixDigits(prefix).flatMap{ digits =>
      Try {
        //val charsToSlice = digits.length
        //val decoded: BinaryData = Base58.decode(s)
        //val decodedByteArr: Array[Byte] = decoded.data.toArray
        //decodedByteArr.slice(charsToSlice, decodedByteArr.length)
        val decoded = Base58Check.decode(s)
        decoded._2.data.toArray
      }
    }
}
