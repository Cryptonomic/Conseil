package tech.cryptonomic.conseil.common.ethereum.domain

import tech.cryptonomic.conseil.common.ethereum.EthereumFixtures
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class BytecodeTest extends ConseilSpec with EthereumFixtures {
  "Bytecode" should {
    "normalize bytecode" in {
      Bytecode("60").normalized shouldBe "60"
      Bytecode("0x60").normalized shouldBe "60"
    }

    "return SHA-1 hash of normalized bytecode" in {
      Bytecode("0xabc").hash shouldBe "0xa9993e364706816aba3e25717850c26c9cd0d89d"
      Bytecode(BytecodeFixtures.erc721).hash shouldBe "0x3ca107ba452ff04fee86aa755f151f6b4752338a"
    }

    "return opcodes from bytecode" in {
      Bytecode("60406318160ddd00").opcodes shouldBe Seq(
        Opcode(0x0, Instructions.PUSH1, BigInt(0x40)),
        Opcode(0x2, Instructions.PUSH4, BigInt(0x18160ddd)),
        Opcode(0x7, Instructions.STOP, BigInt(0x0))
      )
    }

    "check if bytecode implements function" in {
      Bytecode("60406318160ddd00").implements("totalSupply()") shouldBe true
      Bytecode("0x0").implements("totalSupply()") shouldBe false
    }

    "check if bytecode is a erc20 contract" in {
      Bytecode(BytecodeFixtures.erc20).isErc20 shouldBe true
      Bytecode(BytecodeFixtures.contract).isErc20 shouldBe false
    }

    "check if bytecode is a erc721 contract" in {
      Bytecode(BytecodeFixtures.erc721).isErc721 shouldBe true
      Bytecode(BytecodeFixtures.contract).isErc721 shouldBe false
    }
  }
}
