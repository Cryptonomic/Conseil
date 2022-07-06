package tech.cryptonomic.conseil.indexer.tezos.michelson.contracts

import tech.cryptonomic.conseil.common.testkit.ConseilSpec

/** Verifies general handling functions on micheline/michelson scripts */
class SmartContractsTest extends ConseilSpec {

  "The smart contract module" should {

    "correctly hash a delegation contract script" in {
      SmartContracts.hashMichelsonScript(babylonDelegation) shouldBe "a585489ffaee60d07077059539d5bfc8"
    }

    "generate a consistent hash independently of how the contract's parts are layed out" in {

      val version1 =
        """parameter (int :p) ;
        |storage int ;
        |code { UNPAIR ; SWAP ; DROP ; NIL operation ; PAIR }""".stripMargin

      val version2 =
        """storage int ;
        |parameter (int :p) ;
        |code { UNPAIR ; SWAP ; DROP ; NIL operation ; PAIR }""".stripMargin

      val version3 =
        """code { UNPAIR ; SWAP ; DROP ; NIL operation ; PAIR }
        |parameter (int :p) ;
        |storage int ;""".stripMargin

      val hash1 = SmartContracts.hashMichelsonScript(version1)
      val hash2 = SmartContracts.hashMichelsonScript(version2)
      val hash3 = SmartContracts.hashMichelsonScript(version3)

      hash1 shouldBe hash2
      hash1 shouldBe hash3

    }

    "ignore script comments when generating a contract script hash" in {
      val uncommented =
        """parameter (int :p) ;
        |storage int ;
        |code { UNPAIR ; SWAP ; DROP ; NIL operation ; PAIR }""".stripMargin

      val commented =
        """parameter (int :p) ; # takes an int
        |storage int ;            # defines an int storage
        |code { UNPAIR ; SWAP ; DROP ; NIL operation ; PAIR } # puts the value in storage""".stripMargin

      val uncommentedHash = SmartContracts.hashMichelsonScript(uncommented)
      val commentedHash = SmartContracts.hashMichelsonScript(commented)

      uncommentedHash shouldBe commentedHash
    }
  }

  private val babylonDelegation =
    """parameter (or (lambda %do unit (list operation)) (unit %default));
storage key_hash;
code { { { DUP ; CAR ; DIP { CDR } } } ;
       IF_LEFT { PUSH mutez 0 ;
                 AMOUNT ;
                 { { COMPARE ; EQ } ; IF {} { { UNIT ; FAILWITH } } } ;
                 { DIP { DUP } ; SWAP } ;
                 IMPLICIT_ACCOUNT ;
                 ADDRESS ;
                 SENDER ;
                 { { COMPARE ; EQ } ; IF {} { { UNIT ; FAILWITH } } } ;
                 UNIT ;
                 EXEC ;
                 PAIR }
               { DROP ;
                 NIL operation ;
                 PAIR } }"""
}
