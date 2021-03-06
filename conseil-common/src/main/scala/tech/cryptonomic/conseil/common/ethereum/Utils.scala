package tech.cryptonomic.conseil.common.ethereum

import scala.util.Try

import scorex.crypto.hash.Keccak256

/**
  * Utils methods required by the Ethereum integration.
  */
object Utils {

  /**
    * Decode hex string.
    */
  def hexToString(value: String): String =
    new String(value.stripPrefix("0x").grouped(2).toArray.map(Integer.parseInt(_, 16).toByte), "UTF-8").trim

  /**
    * Create SHA-3 signature from the first 4 bytes of the given string.
    */
  def keccak(value: String): String =
    Keccak256(value.replace(" ", "")).take(4).map("%02X".format(_)).mkString

  /**
    * Convert hex string to [[BigDecimal]]
    */
  def hexStringToBigDecimal(value: String): BigDecimal =
    BigDecimal(Try(BigInt(value.stripPrefix("0x"), 16)).getOrElse(BigInt(0)))

  /**
    * Truncate empty hex string '0x'
    */
  def truncateEmptyHexString(value: String): String = value match {
    case s if s.endsWith("0x") => ""
    case s => s
  }

  def hexToInt(value: String): Option[Int] =
    Try(Integer.decode(value)).map(_.toInt).toOption

}
