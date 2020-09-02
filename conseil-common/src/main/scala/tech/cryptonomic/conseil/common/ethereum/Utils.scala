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
    value.stripPrefix("0x").grouped(2).toArray.map(Integer.parseInt(_, 16).toChar).mkString.trim

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

}
