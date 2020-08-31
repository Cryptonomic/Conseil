package tech.cryptonomic.conseil.common.ethereum.domain

import tech.cryptonomic.conseil.common.ethereum.Utils

/**
  * Ethereum contract bytecode disassembler.
  * It allows to chech if given contract is ERC20 or ERC721.
  * The code is based on https://github.com/ethereum/evmdasm
  */
case class Bytecode(value: String) {

  /**
    * Bytecode without "0x" prefix.
    */
  lazy val normalized = Utils.remove0x(value)

  /**
    * To turn the compiled bytecode into a list of operations,
    * we have to divide it every 2 elements and check a
    * function signature from the defined [[Instruction]] registry.
    *
    * Push operations require to take next n elements to compute given function parameters.
    */
  lazy val opcodes: Seq[Opcode] =
    normalized
      .grouped(2)
      .map(Integer.valueOf(_, 16))
      .zipWithIndex
      .foldLeft((0, Seq.empty[Opcode])) {
        case (opcodes, (bytes, offset)) =>
          opcodes match {
            case (0, opcodes) =>
              Instructions.registry.find(_.opcode == bytes) match {
                case Some(instruction) if instruction.args > 0 =>
                  (
                    instruction.args,
                    opcodes :+ Opcode(
                          offset,
                          instruction,
                          BigInt(
                            normalized.substring(
                              offset * 2 + 2,
                              Integer.min(offset * 2 + 2 + instruction.args * 2, normalized.size)
                            ),
                            16
                          )
                        )
                  )
                case Some(instruction) => (0, opcodes :+ Opcode(offset, instruction, 0))
                case None => (0, opcodes) // opcode not found, continue
              }
            case (skip, opcodes) => (skip - 1, opcodes)
          }

      }
      ._2

  /**
    * Check if bytecode implements a particular function.
    * Every function call is represented in the bytecode 
    * by PUSH4 command with 4 bytes sigsignature.
    * 
    * example:
    * ...
    * DUP1
    * PUSH4 4-byte-function-signature
    * STOP
    * ...
    * }}}
    */
  def implements(function: String): Boolean =
    opcodes.exists(
      o => o.instruction.name == "PUSH4" && o.parameters == BigInt(Utils.keccak(function), 16)
    )

  // https://github.com/ethereum/EIPs/blob/master/EIPS/eip-20.md
  lazy val isErc20: Boolean =
    implements("totalSupply()") &&
      implements("balanceOf(address)") &&
      implements("transfer(address,uint256)") &&
      implements("transferFrom(address,address,uint256)") &&
      implements("approve(address,uint256)") &&
      implements("allowance(address,address)")

  // https://github.com/ethereum/EIPs/blob/master/EIPS/eip-721.md
  lazy val isErc721: Boolean =
    implements("balanceOf(address)") &&
      implements("ownerOf(uint256)") &&
      (implements("transfer(address,uint256)") || implements("transferFrom(address,address,uint256)")) &&
      implements("approve(address,uint256)") &&
      implements("getApproved(uint256)") &&
      implements("isApprovedForAll(address,address)") &&
      implements("safeTransferFrom(address,address,uint256)") &&
      implements("safeTransferFrom(address,address,uint256,bytes)")
}
