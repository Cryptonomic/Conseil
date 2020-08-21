package tech.cryptonomic.conseil.common.ethereum.domain

case class Opcode(offset: Int, instruction: Instruction, parameters: BigInt)
