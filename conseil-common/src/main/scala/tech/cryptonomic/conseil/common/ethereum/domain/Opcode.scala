package tech.cryptonomic.conseil.common.ethereum.domain

/**
  * Ethereum bytecode's opcode.
  */
case class Opcode(offset: Int, instruction: Instruction, parameters: BigInt)
