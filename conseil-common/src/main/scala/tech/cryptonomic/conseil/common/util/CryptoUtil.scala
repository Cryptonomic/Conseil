package tech.cryptonomic.conseil.common.util

import fr.acinq.bitcoin.{Base58, Base58Check}
import scorex.util.encode.{Base16 => Hex}

import scala.collection.JavaConverters._
import fr.acinq.bitcoin.scalacompat.Crypto._
import scodec.bits._

import scala.util.Try
import scala.util.Failure

/**
  * Utility function for common cryptographic operations.
  */
object CryptoUtil {

  final case class KeyStore(publicKey: String, privateKey: String, publicKeyHash: String)

  /** Get byte prefix for Base58Check encoding and decoding of a given type of data.
    * @param prefix The type of data
    * @return       Byte prefix
    */
  private def getBase58BytesForPrefix(prefix: String): Try[List[Byte]] = Try {
    prefix.toLowerCase match {
      case "tz1" => List(6, 161, 159).map(_.toByte)
      case "tz2" => List(6, 161, 161).map(_.toByte)
      case "tz3" => List(6, 161, 164).map(_.toByte)
      case "kt1" => List(2, 90, 121).map(_.toByte)
      case "edpk" => List(13, 15, 37, 217).map(_.toByte)
      case "edsk" => List(43, 246, 78, 7).map(_.toByte)
      case "edsig" => List(9, 245, 205, 134, 18).map(_.toByte)
      case "op" => List(5, 116).map(_.toByte)
      case "expr" => List(13, 44, 64, 27).map(_.toByte)
      case _ => throw new Exception(s"Could not find prefix for $prefix!")
    }
  }

  /** Base58Check encodes a given binary payload using a given prefix.
    * @param payload  Binary payload
    * @param prefix   Prefix
    * @return         Encoded string
    */
  def base58CheckEncode(payload: Seq[Byte], prefix: String): Try[String] =
    getBase58BytesForPrefix(prefix).map { prefix =>
      Base58Check.encode(prefix.toArray, payload.toArray)
    }

  /** Base58Check decodes a given binary payload using a given prefix.
    * @param s      Base58Check-encoded string
    * @param prefix Prefix
    * @return       Decoded bytes
    */
  def base58CheckDecode(s: String, prefix: String): Try[Seq[Byte]] =
    getBase58BytesForPrefix(prefix).map { prefix =>
      val charsToSlice = prefix.length
      val decodeResult = Base58Check.decode(s)
      val first = decodeResult.getFirst.toByte
      val rest = decodeResult.getSecond.toList
      val decodedBytes = first :: rest
      decodedBytes.drop(charsToSlice)
    }

  /** Decodes the account b58-check address as an hexadecimal bytestring
    * It's almost the inverse of [[unpackAddress]]
    */
  def packAddress(b58Address: String): Try[String] = {

    def dataLength(num: Long) = ("0000000" + num.toHexString).takeRight(8)

    def wrap(hexString: String): String =
      b58Address.take(3).toLowerCase match {
        case "tz1" => "0000" + hexString
        case "tz2" => "0001" + hexString
        case "tz3" => "0002" + hexString
        case "kt1" => "01" + hexString + "00"
      }

    //what if the wrapped length is odd? Technically it shouldn't be possible since
    //bytes are always made of 2 chars and we only pad them with other bytes.
    base58CheckDecode(b58Address, b58Address.take(3).toLowerCase).map { bytes =>
      val wrapped = wrap(Hex.encode(bytes.toArray))
      s"050a${dataLength(wrapped.length / 2)}$wrapped"
    }
  }

  //used as hints to identify hex-encoded account address types
  private val tz1 = "0000".r
  private val tz2 = "0001".r
  private val tz3 = "0002".r
  private val kt1 = "01.{2}".r

  /** Encodes the hexadecimal bytestring to a b58-check account address
    * It's almost the inverse of [[packAddress]]
    */
  def readAddress(hexEncoded: String): Try[String] = {
    val (hint, content) = hexEncoded.length() match {
      case 42 =>
        val hint = "00" + hexEncoded.take(2)
        val content = hexEncoded.drop(2)
        hint -> content
      case 44 =>
        val hint = hexEncoded.take(4)
        val content = hint match {
          case kt1() => hexEncoded.drop(2).take(40)
          case _ => hexEncoded.drop(4)
        }
        hint -> content
    }
    for {
      hexContent <- Hex.decode(content)
      address <- hint match {
        case tz1() =>
          CryptoUtil.base58CheckEncode(hexContent, "tz1")
        case tz2() =>
          CryptoUtil.base58CheckEncode(hexContent, "tz2")
        case tz3() =>
          CryptoUtil.base58CheckEncode(hexContent, "tz3")
        case kt1() =>
          CryptoUtil.base58CheckEncode(hexContent, "kt1")
        case _ =>
          Failure(new IllegalArgumentException(s"No address can be decoded from the hex string '$hexEncoded'"))
      }
    } yield address
  }

  /** When encoding numbers as bytes in michelson/micheline they use
    * an unusual form, it's little endian base-128 but use the full bytes
    * to keep additional info.
    *
    * Please refer to the last paragraph of
    * https://medium.com/the-cryptonomic-aperiodical/the-magic-and-mystery-of-the-micheline-binary-format-33bf85699bef
    * or to the original implementation: https://github.com/ocaml/Zarith
    *
    * @param hexEncoded ZArith encoding as hex string
    * @return a possibly valid signed integer of arbitrary magnitude
    */
  def decodeZarithNumber(hexEncoded: String): Try[BigInt] = {
    import scorex.util.encode.{Base16 => Hex}

    //the sign is defined by the second bit in the hex-string
    val signMask: Byte = 0x40

    /* base128 little-endian decoding with special treat for the lower byte */
    def readSigned(bytes: Array[Byte]): BigInt = {
      val positive = (bytes.head & signMask) == 0
      val masked = ((bytes.head & 0x3f) +: bytes.tail.map(_ & 0x7f)).map(_.toByte)

      val result = masked.zipWithIndex.foldRight(BigInt(0)) {
        case ((byte, 0), bigInt) =>
          bigInt | BigInt(byte)
        case ((byte, index), bigInt) =>
          val intBits = BigInt(byte) << (7 * index - 1)
          bigInt | intBits
      }

      if (positive) result else -result
    }

    Hex
      .decode(hexEncoded)
      .map { bytes =>
        /* the first bit signals that there's more to read
         * we keep all those, and add the one following
         */
        val highBitSet = bytes.takeWhile(_ < 0)
        highBitSet :+ bytes(highBitSet.size)
      }
      .map(readSigned)

  }

}
