package tech.cryptonomic.conseil.common.ethereum

import scorex.crypto.hash.Keccak256

/**
  * Utils methods required by the Ethereum integration.
  */
object Utils {
  def functionSignatureTo4byteHexSelector(function: String): BigInt =
    BigInt(Keccak256(function.replace(" ", "")).take(4).map("%02X".format(_)).mkString, 16)
}
