package tech.cryptonomic.conseil.common.bitcoin

object BitcoinTypes {

  /** Case class representing hash to identify blocks across many block chains */
  final case class BitcoinBlockHash(value: String) extends AnyVal

}
