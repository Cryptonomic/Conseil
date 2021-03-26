package tech.cryptonomic.conseil.common.ethereum.domain

/**
  *  Standard implemented by ethereum contract
  */
sealed abstract class TokenStandard(val value: String)

object TokenStandards {
  case object ERC20 extends TokenStandard("ERC20")
  case object ERC721 extends TokenStandard("ERC721")
}
