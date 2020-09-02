package tech.cryptonomic.conseil.common.ethereum.domain

/**
  * Contract opcode instruction.
  */
sealed abstract class Instruction(val opcode: Int, val name: String, val args: Int = 0)

object Instructions {
  // Stop and Arithmetic Operations
  case object STOP extends Instruction(opcode = 0x00, name = "STOP")
  case object ADD extends Instruction(opcode = 0x01, name = "ADD")
  case object MUL extends Instruction(opcode = 0x02, name = "MUL")
  case object SUB extends Instruction(opcode = 0x03, name = "SUB")
  case object DIV extends Instruction(opcode = 0x04, name = "DIV")
  case object SDIV extends Instruction(opcode = 0x05, name = "SDIV")
  case object MOD extends Instruction(opcode = 0x06, name = "MOD")
  case object SMOD extends Instruction(opcode = 0x07, name = "SMOD")
  case object ADDMOD extends Instruction(opcode = 0x08, name = "ADDMOD")
  case object MULMOD extends Instruction(opcode = 0x09, name = "MULMOD")
  case object EXP extends Instruction(opcode = 0x0a, name = "EXP")
  case object SIGNEXTEND extends Instruction(opcode = 0x0b, name = "SIGNEXTEND")

  // Comparison & Bitwise Logic Operations
  case object LT extends Instruction(opcode = 0x10, name = "LT")
  case object GT extends Instruction(opcode = 0x11, name = "GT")
  case object SLT extends Instruction(opcode = 0x12, name = "SLT")
  case object SGT extends Instruction(opcode = 0x13, name = "SGT")
  case object EQ extends Instruction(opcode = 0x14, name = "EQ")
  case object ISZERO extends Instruction(opcode = 0x15, name = "ISZERO")
  case object AND extends Instruction(opcode = 0x16, name = "AND")
  case object OR extends Instruction(opcode = 0x17, name = "OR")
  case object XOR extends Instruction(opcode = 0x18, name = "XOR")
  case object NOT extends Instruction(opcode = 0x19, name = "NOT")
  case object BYTE extends Instruction(opcode = 0x1a, name = "BYTE")
  case object SHL extends Instruction(opcode = 0x1b, name = "SHL")
  case object SHR extends Instruction(opcode = 0x1c, name = "SHR")
  case object SAR extends Instruction(opcode = 0x1d, name = "SAR")

  // SHA3
  case object SHA3 extends Instruction(opcode = 0x20, name = "SHA3")

  // Environmental Information
  case object ADDRESS extends Instruction(opcode = 0x30, name = "ADDRESS")
  case object BALANCE extends Instruction(opcode = 0x31, name = "BALANCE")
  case object ORIGIN extends Instruction(opcode = 0x32, name = "ORIGIN")
  case object CALLER extends Instruction(opcode = 0x33, name = "CALLER")
  case object CALLVALUE extends Instruction(opcode = 0x34, name = "CALLVALUE")
  case object CALLDATALOAD extends Instruction(opcode = 0x35, name = "CALLDATALOAD")
  case object CALLDATASIZE extends Instruction(opcode = 0x36, name = "CALLDATASIZE")
  case object CALLDATACOPY extends Instruction(opcode = 0x37, name = "CALLDATACOPY")
  case object CODESIZE extends Instruction(opcode = 0x38, name = "CODESIZE")
  case object CODECOPY extends Instruction(opcode = 0x39, name = "CODECOPY")
  case object GASPRICE extends Instruction(opcode = 0x3a, name = "GASPRICE")
  case object EXTCODESIZE extends Instruction(opcode = 0x3b, name = "EXTCODESIZE")
  case object EXTCODECOPY extends Instruction(opcode = 0x3c, name = "EXTCODECOPY")
  case object RETURNDATASIZE extends Instruction(opcode = 0x3d, name = "RETURNDATASIZE")
  case object RETURNDATACOPY extends Instruction(opcode = 0x3e, name = "RETURNDATACOPY")
  case object EXTCODEHASH extends Instruction(opcode = 0x3f, name = "EXTCODEHASH")

  // Block Information
  case object BLOCKHASH extends Instruction(opcode = 0x40, name = "BLOCKHASH")
  case object COINBASE extends Instruction(opcode = 0x41, name = "COINBASE")
  case object TIMESTAMP extends Instruction(opcode = 0x42, name = "TIMESTAMP")
  case object NUMBER extends Instruction(opcode = 0x43, name = "NUMBER")
  case object DIFFICULTY extends Instruction(opcode = 0x44, name = "DIFFICULTY")
  case object GASLIMIT extends Instruction(opcode = 0x45, name = "GASLIMIT")

  // Stack, Memory, Storage and Flow Operations
  case object POP extends Instruction(opcode = 0x50, name = "POP")

  // dummy is only there to indicate that there is a pop()
  case object MLOAD extends Instruction(opcode = 0x51, name = "MLOAD")
  case object MSTORE extends Instruction(opcode = 0x52, name = "MSTORE")
  case object MSTORE8 extends Instruction(opcode = 0x53, name = "MSTORE8")
  case object SLOAD extends Instruction(opcode = 0x54, name = "SLOAD")
  case object SSTORE extends Instruction(opcode = 0x55, name = "SSTORE")
  case object JUMP extends Instruction(opcode = 0x56, name = "JUMP")
  case object JUMPI extends Instruction(opcode = 0x57, name = "JUMPI")
  case object PC extends Instruction(opcode = 0x58, name = "PC")
  case object MSIZE extends Instruction(opcode = 0x59, name = "MSIZE")
  case object GAS extends Instruction(opcode = 0x5a, name = "GAS")
  case object JUMPDEST extends Instruction(opcode = 0x5b, name = "JUMPDEST")

  // Stack Push Operations
  case object PUSH1 extends Instruction(opcode = 0x60, name = "PUSH1", args = 1)
  case object PUSH2 extends Instruction(opcode = 0x61, name = "PUSH2", args = 2)
  case object PUSH3 extends Instruction(opcode = 0x62, name = "PUSH3", args = 3)
  case object PUSH4 extends Instruction(opcode = 0x63, name = "PUSH4", args = 4)
  case object PUSH5 extends Instruction(opcode = 0x64, name = "PUSH5", args = 5)
  case object PUSH6 extends Instruction(opcode = 0x65, name = "PUSH6", args = 6)
  case object PUSH7 extends Instruction(opcode = 0x66, name = "PUSH7", args = 7)
  case object PUSH8 extends Instruction(opcode = 0x67, name = "PUSH8", args = 8)
  case object PUSH9 extends Instruction(opcode = 0x68, name = "PUSH9", args = 9)
  case object PUSH10 extends Instruction(opcode = 0x69, name = "PUSH10", args = 10)
  case object PUSH11 extends Instruction(opcode = 0x6a, name = "PUSH11", args = 11)
  case object PUSH12 extends Instruction(opcode = 0x6b, name = "PUSH12", args = 12)
  case object PUSH13 extends Instruction(opcode = 0x6c, name = "PUSH13", args = 13)
  case object PUSH14 extends Instruction(opcode = 0x6d, name = "PUSH14", args = 14)
  case object PUSH15 extends Instruction(opcode = 0x6e, name = "PUSH15", args = 15)
  case object PUSH16 extends Instruction(opcode = 0x6f, name = "PUSH16", args = 16)
  case object PUSH17 extends Instruction(opcode = 0x70, name = "PUSH17", args = 17)
  case object PUSH18 extends Instruction(opcode = 0x71, name = "PUSH18", args = 18)
  case object PUSH19 extends Instruction(opcode = 0x72, name = "PUSH19", args = 19)
  case object PUSH20 extends Instruction(opcode = 0x73, name = "PUSH20", args = 20)
  case object PUSH21 extends Instruction(opcode = 0x74, name = "PUSH21", args = 21)
  case object PUSH22 extends Instruction(opcode = 0x75, name = "PUSH22", args = 22)
  case object PUSH23 extends Instruction(opcode = 0x76, name = "PUSH23", args = 23)
  case object PUSH24 extends Instruction(opcode = 0x77, name = "PUSH24", args = 24)
  case object PUSH25 extends Instruction(opcode = 0x78, name = "PUSH25", args = 25)
  case object PUSH26 extends Instruction(opcode = 0x79, name = "PUSH26", args = 26)
  case object PUSH27 extends Instruction(opcode = 0x7a, name = "PUSH27", args = 27)
  case object PUSH28 extends Instruction(opcode = 0x7b, name = "PUSH28", args = 28)
  case object PUSH29 extends Instruction(opcode = 0x7c, name = "PUSH29", args = 29)
  case object PUSH30 extends Instruction(opcode = 0x7d, name = "PUSH30", args = 30)
  case object PUSH31 extends Instruction(opcode = 0x7e, name = "PUSH31", args = 31)
  case object PUSH32 extends Instruction(opcode = 0x7f, name = "PUSH32", args = 32)

  // Duplication Operations
  case object DUP1 extends Instruction(opcode = 0x80, name = "DUP1")
  case object DUP2 extends Instruction(opcode = 0x81, name = "DUP2")
  case object DUP3 extends Instruction(opcode = 0x82, name = "DUP3")
  case object DUP4 extends Instruction(opcode = 0x83, name = "DUP4")
  case object DUP5 extends Instruction(opcode = 0x84, name = "DUP5")
  case object DUP6 extends Instruction(opcode = 0x85, name = "DUP6")
  case object DUP7 extends Instruction(opcode = 0x86, name = "DUP7")
  case object DUP8 extends Instruction(opcode = 0x87, name = "DUP8")
  case object DUP9 extends Instruction(opcode = 0x88, name = "DUP9")
  case object DUP10 extends Instruction(opcode = 0x89, name = "DUP10")
  case object DUP11 extends Instruction(opcode = 0x8a, name = "DUP11")
  case object DUP12 extends Instruction(opcode = 0x8b, name = "DUP12")
  case object DUP13 extends Instruction(opcode = 0x8c, name = "DUP13")
  case object DUP14 extends Instruction(opcode = 0x8d, name = "DUP14")
  case object DUP15 extends Instruction(opcode = 0x8e, name = "DUP15")
  case object DUP16 extends Instruction(opcode = 0x8f, name = "DUP16")

  // Exchange Operations
  case object SWAP1 extends Instruction(opcode = 0x90, name = "SWAP1")
  case object SWAP2 extends Instruction(opcode = 0x91, name = "SWAP2")
  case object SWAP3 extends Instruction(opcode = 0x92, name = "SWAP3")
  case object SWAP4 extends Instruction(opcode = 0x93, name = "SWAP4")
  case object SWAP5 extends Instruction(opcode = 0x94, name = "SWAP5")
  case object SWAP6 extends Instruction(opcode = 0x95, name = "SWAP6")
  case object SWAP7 extends Instruction(opcode = 0x96, name = "SWAP7")
  case object SWAP8 extends Instruction(opcode = 0x97, name = "SWAP8")
  case object SWAP9 extends Instruction(opcode = 0x98, name = "SWAP9")
  case object SWAP10 extends Instruction(opcode = 0x99, name = "SWAP10")
  case object SWAP11 extends Instruction(opcode = 0x9a, name = "SWAP11")
  case object SWAP12 extends Instruction(opcode = 0x9b, name = "SWAP12")
  case object SWAP13 extends Instruction(opcode = 0x9c, name = "SWAP13")
  case object SWAP14 extends Instruction(opcode = 0x9d, name = "SWAP14")
  case object SWAP15 extends Instruction(opcode = 0x9e, name = "SWAP15")
  case object SWAP16 extends Instruction(opcode = 0x9f, name = "SWAP16")

  // Logging Operations
  case object LOG0 extends Instruction(opcode = 0xa0, name = "LOG0")
  case object LOG1 extends Instruction(opcode = 0xa1, name = "LOG1")
  case object LOG2 extends Instruction(opcode = 0xa2, name = "LOG2")
  case object LOG3 extends Instruction(opcode = 0xa3, name = "LOG3")
  case object LOG4 extends Instruction(opcode = 0xa4, name = "LOG4")

  // unofficial opcodes used for parsing.
  case object UNOFFICIAL_PUSH extends Instruction(opcode = 0xb0, name = "UNOFFICIAL_PUSH")
  case object UNOFFICIAL_DUP extends Instruction(opcode = 0xb1, name = "UNOFFICIAL_DUP")
  case object UNOFFICIAL_SWAP extends Instruction(opcode = 0xb2, name = "UNOFFICIAL_SWAP")

  // System Operations
  case object CREATE extends Instruction(opcode = 0xf0, name = "CREATE")
  case object CALL extends Instruction(opcode = 0xf1, name = "CALL")
  case object CALLCODE extends Instruction(opcode = 0xf2, name = "CALLCODE")
  case object RETURN extends Instruction(opcode = 0xf3, name = "RETURN")
  case object DELEGATECALL extends Instruction(opcode = 0xf4, name = "DELEGATECALL")
  case object CREATE2 extends Instruction(opcode = 0xf5, name = "CREATE2")

  // Newer opcode
  case object STATICCALL extends Instruction(opcode = 0xfa, name = "STATICCALL")
  case object REVERT extends Instruction(opcode = 0xfd, name = "REVERT")

  // Halt Execution, Mark for deletion
  case object SELFDESTRUCT extends Instruction(opcode = 0xff, name = "SELFDESTRUCT")

  val registry = Seq(
    STOP,
    ADD,
    MUL,
    SUB,
    DIV,
    SDIV,
    MOD,
    SMOD,
    ADDMOD,
    MULMOD,
    EXP,
    SIGNEXTEND,
    LT,
    GT,
    SLT,
    SGT,
    EQ,
    ISZERO,
    AND,
    OR,
    XOR,
    NOT,
    BYTE,
    SHL,
    SHR,
    SAR,
    SHA3,
    ADDRESS,
    BALANCE,
    ORIGIN,
    CALLER,
    CALLVALUE,
    CALLDATALOAD,
    CALLDATASIZE,
    CALLDATACOPY,
    CODESIZE,
    CODECOPY,
    GASPRICE,
    EXTCODESIZE,
    EXTCODECOPY,
    RETURNDATASIZE,
    RETURNDATACOPY,
    EXTCODEHASH,
    BLOCKHASH,
    COINBASE,
    TIMESTAMP,
    NUMBER,
    DIFFICULTY,
    GASLIMIT,
    POP,
    MLOAD,
    MSTORE,
    MSTORE8,
    SLOAD,
    SSTORE,
    JUMP,
    JUMPI,
    PC,
    MSIZE,
    GAS,
    JUMPDEST,
    PUSH1,
    PUSH2,
    PUSH3,
    PUSH4,
    PUSH5,
    PUSH6,
    PUSH7,
    PUSH8,
    PUSH9,
    PUSH10,
    PUSH11,
    PUSH12,
    PUSH13,
    PUSH14,
    PUSH15,
    PUSH16,
    PUSH17,
    PUSH18,
    PUSH19,
    PUSH20,
    PUSH21,
    PUSH22,
    PUSH23,
    PUSH24,
    PUSH25,
    PUSH26,
    PUSH27,
    PUSH28,
    PUSH29,
    PUSH30,
    PUSH31,
    PUSH32,
    DUP1,
    DUP2,
    DUP3,
    DUP4,
    DUP5,
    DUP6,
    DUP7,
    DUP8,
    DUP9,
    DUP10,
    DUP11,
    DUP12,
    DUP13,
    DUP14,
    DUP15,
    DUP16,
    SWAP1,
    SWAP2,
    SWAP3,
    SWAP4,
    SWAP5,
    SWAP6,
    SWAP7,
    SWAP8,
    SWAP9,
    SWAP10,
    SWAP11,
    SWAP12,
    SWAP13,
    SWAP14,
    SWAP15,
    SWAP16,
    LOG0,
    LOG1,
    LOG2,
    LOG3,
    LOG4,
    UNOFFICIAL_PUSH,
    UNOFFICIAL_DUP,
    UNOFFICIAL_SWAP,
    CREATE,
    CALL,
    CALLCODE,
    RETURN,
    DELEGATECALL,
    CREATE2,
    STATICCALL,
    REVERT,
    SELFDESTRUCT
  )

  lazy val byName = registry.map(i => i.name -> i).toMap
}
