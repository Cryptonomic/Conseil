package tech.cryptonomic.conseil.common.ethereum

object EthereumTypes {

  /** Case class representing hash to identify blocks across many block chains */
  final case class EthereumBlockHash(value: String) extends AnyVal

}
