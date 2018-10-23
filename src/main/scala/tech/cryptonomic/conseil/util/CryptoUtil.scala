package tech.cryptonomic.conseil.util

import fr.acinq.bitcoin.Base58Check

import scala.util.Try

/**
  * Utility function for common cryptographic operations.
  */
object CryptoUtil {

  final case class KeyStore(publicKey: String, privateKey: String, publicKeyHash: String)

  /**
    * Get byte prefix for Base58Check encoding and decoding of a given type of data.
    * @param prefix The type of data
    * @return       Byte prefix
    */
  private def getBase58BytesForPrefix(prefix: String): Try[List[Byte]] = Try {
    prefix match {
      case "tz1"    => List[Byte](6.toByte, 161.toByte, 159.toByte)
      case "edpk"   => List[Byte](13.toByte, 15.toByte, 37.toByte, 217.toByte)
      case "edsk"   => List[Byte](43.toByte, 246.toByte, 78.toByte, 7.toByte)
      case "edsig"  => List[Byte](9.toByte, 245.toByte, 205.toByte, 134.toByte, 18.toByte)
      case "op"     => List[Byte](5.toByte, 116.toByte)
      case _        => throw new Exception(s"Could not find prefix for $prefix!")
    }
  }

  /**
    * Base58Check encodes a given binary payload using a given prefix.
    * @param payload  Binary payload
    * @param prefix   Prefix
    * @return         Encoded string
    */
  def base58CheckEncode(payload: Seq[Byte], prefix: String): Try[String] =
    getBase58BytesForPrefix(prefix).flatMap { prefix =>
      Try {
        Base58Check.encode(prefix, payload)
      }
    }

  /**
    * Base58Check decodes a given binary payload using a given prefix.
    * @param s      Base58Check-encoded string
    * @param prefix Prefix
    * @return       Decoded bytes
    */
  def base58CheckDecode(s: String, prefix: String): Try[Seq[Byte]] =
    getBase58BytesForPrefix(prefix).flatMap{ prefix =>
      Try {
        val charsToSlice = prefix.length
        val decoded = Base58Check.decode(s)
        val decodedBytes = decoded._1  :: decoded._2.data.toList
        decodedBytes.toArray.slice(charsToSlice, decodedBytes.length)
      }
    }
}
