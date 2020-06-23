package tech.cryptonomic.conseil.common.bitcoin

import java.sql.Timestamp

import tech.cryptonomic.conseil.common.bitcoin.rpc.json._

/**
  * Fixtures for Bitcoin based on block at height 102000 with hash 00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58
  */
trait BitcoinFixtures {

  /**
    * Fixtures containing raw json responses from the JSON-RPC server.
    */
  object JsonFixtures {
    val getBlockchainInfo = """[{
                              |  "result": {
                              |    "chain": "main",
                              |    "blocks": 635417,
                              |    "headers": 635417,
                              |    "bestblockhash": "00000000000000000002c8f5d2eb0d8431e48307308c0e675bb1d4647ca66342",
                              |    "difficulty": 15784744305477.41,
                              |    "mediantime": 1592568319,
                              |    "verificationprogress": 0.9999916250763577,
                              |    "initialblockdownload": false,
                              |    "chainwork": "0000000000000000000000000000000000000000107f9be18bfd070bcdd6ab5e",
                              |    "size_on_disk": 321764142146,
                              |    "pruned": false,
                              |    "softforks": {
                              |      "bip34": {
                              |        "type": "buried",
                              |        "active": true,
                              |        "height": 227931
                              |       },
                              |       "bip66": {
                              |         "type": "buried",
                              |         "active": true,
                              |         "height": 363725
                              |        },
                              |        "bip65": {
                              |          "type": "buried",
                              |          "active": true,
                              |          "height": 388381
                              |        },
                              |        "csv": {
                              |          "type": "buried",
                              |          "active": true,
                              |          "height": 419328
                              |        },
                              |        "segwit": {
                              |          "type": "buried",
                              |          "active": true,
                              |          "height": 481824
                              |        }
                              |      },
                              |      "warnings": ""
                              |  },
                              |  "id": "requestId"
                              |}]""".stripMargin

    val getBlockHash = """[{
                         |  "result": "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
                         |  "id": "requestId"
                         |}]""".stripMargin

    val getBlock = """[{
                     |  "result": {
                     |    "hash": "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
                     |    "confirmations": 533285,
                     |    "strippedsize": 215,
                     |    "size": 215,
                     |    "weight": 860,
                     |    "height": 102000,
                     |    "version": 1,
                     |    "versionHex": "00000001",
                     |    "merkleroot": "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
                     |    "tx": [
                     |      "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01"
                     |     ],
                     |     "time": 1294691980,
                     |     "mediantime": 1294691440,
                     |     "nonce": 3851572992,
                     |     "bits": "1b0404cb",
                     |     "difficulty": 16307.42093852398,
                     |     "chainwork": "00000000000000000000000000000000000000000000000008203c8509509ae0",
                     |     "nTx": 1,
                     |     "previousblockhash": "0000000000038b80cf5db1173e96f2290cfda12c505b0fe1bd37d6975e164a8a",
                     |     "nextblockhash": "0000000000035107dce8eb675c6fa9a08c7617c109b3553ad8f208dda24065a6"
                     |  },
                     |  "id": "requestId"
                     |}]""".stripMargin

    val getRawTransaction =
      """[{
        |  "result": {
        |    "txid": "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
        |    "hash": "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
        |    "version": 1,
        |    "size": 134,
        |    "vsize": 134,
        |    "weight": 536,
        |    "locktime": 0,
        |    "vin": [
        |      {
        |        "coinbase": "04cb04041b0166",
        |        "sequence": 4294967295
        |      }
        |    ],
        |    "vout": [
        |      {
        |        "value": 50.00000000,
        |        "n": 0,
        |        "scriptPubKey": {
        |          "asm": "0476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332 OP_CHECKSIG",
        |          "hex": "410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac",
        |          "type": "pubkey"
        |        }
        |      }
        |    ],
        |    "hex": "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0704cb04041b0166ffffffff0100f2052a0100000043410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac00000000",
        |    "blockhash": "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
        |    "confirmations": 533285,
        |    "time": 1294691980,
        |    "blocktime": 1294691980
        |  },
        |  "id": "requestId"
        |}]""".stripMargin
  }

  /**
    * Fixtures containing decoded json responses form the the JSON-RPC server.
    */
  object RpcFixtures {
    val blockchainInfo = BlockchainInfo(
      chain = "main",
      blocks = 635417
    )
    val block = Block(
      hash = "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
      size = 215,
      strippedsize = 215,
      weight = 860,
      height = 102000,
      version = 1,
      versionHex = "00000001",
      merkleroot = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
      nonce = 3851572992L,
      bits = "1b0404cb",
      difficulty = 16307.42093852398,
      chainwork = "00000000000000000000000000000000000000000000000008203c8509509ae0",
      nTx = 1,
      previousblockhash = Some("0000000000038b80cf5db1173e96f2290cfda12c505b0fe1bd37d6975e164a8a"),
      nextblockhash = Some("0000000000035107dce8eb675c6fa9a08c7617c109b3553ad8f208dda24065a6"),
      time = 1294691980,
      mediantime = 1294691440,
      tx = List("d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01")
    )

    val input = TransactionInput(
      txid = None,
      vout = None,
      scriptSig = None,
      sequence = 4294967295L,
      coinbase = Some("04cb04041b0166"),
      txinwitness = None
    )

    val output = TransactionOutput(
      txid = None,
      value = Some(50.0),
      n = 0,
      scriptPubKey = ScriptPubKey(
        asm =
          "0476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332 OP_CHECKSIG",
        hex =
          "410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac",
        reqSigs = None,
        `type` = "pubkey",
        addresses = None
      )
    )

    val inputWithTxid = TransactionInput(
      txid = Some("d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01"),
      vout = None,
      scriptSig = None,
      sequence = 4294967295L,
      coinbase = Some("04cb04041b0166"),
      txinwitness = None
    )

    val outputWithTxid = TransactionOutput(
      txid = Some("d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01"),
      value = Some(50.0),
      n = 0,
      scriptPubKey = ScriptPubKey(
        asm =
          "0476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332 OP_CHECKSIG",
        hex =
          "410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac",
        reqSigs = None,
        `type` = "pubkey",
        addresses = None
      )
    )

    val transaction = Transaction(
      txid = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
      blockhash = "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
      hash = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
      hex =
        "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0704cb04041b0166ffffffff0100f2052a0100000043410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac00000000",
      size = 134,
      vsize = 134,
      weight = 536,
      version = 1,
      time = 1294691980,
      locktime = 0,
      blocktime = 1294691980,
      vin = List(input),
      vout = List(output)
    )
  }

  /**
    * Fixtures containing Slick rows.
    */
  object DbFixtures {
    val block = Tables.BlocksRow(
      hash = "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
      size = 215,
      strippedSize = 215,
      weight = 860,
      height = 102000,
      version = 1,
      versionHex = "00000001",
      merkleRoot = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
      nonce = 3851572992L,
      bits = "1b0404cb",
      difficulty = 16307.42, // TODO: there is some bug in Slick, InMemoryDatabaseSetup creates a field with DECIMAL(21,2) instead NUMERIC as it is in the conseil.sql
      chainWork = "00000000000000000000000000000000000000000000000008203c8509509ae0",
      nTx = 1,
      previousBlockHash = Some("0000000000038b80cf5db1173e96f2290cfda12c505b0fe1bd37d6975e164a8a"),
      nextBlockHash = Some("0000000000035107dce8eb675c6fa9a08c7617c109b3553ad8f208dda24065a6"),
      time = new Timestamp(1294691980),
      medianTime = new Timestamp(1294691440)
    )

    val transaction = Tables.TransactionsRow(
      txid = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
      blockhash = "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
      hash = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
      hex =
        "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0704cb04041b0166ffffffff0100f2052a0100000043410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac00000000",
      size = 134,
      vsize = 134,
      weight = 536,
      version = 1,
      lockTime = new Timestamp(0),
      blockTime = new Timestamp(1294691980),
      time = new Timestamp(1294691980)
    )

    val input = Tables.InputsRow(
      txid = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
      vOut = None,
      scriptSigAsm = None,
      scriptSigHex = None,
      sequence = 4294967295L,
      coinbase = Some("04cb04041b0166"),
      txInWitness = None
    )

    val output = Tables.OutputsRow(
      txid = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
      value = Some(50.0),
      n = 0,
      scriptPubKeyAsm =
        "0476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332 OP_CHECKSIG",
      scriptPubKeyHex =
        "410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac",
      scriptPubKeyReqSigs = None,
      scriptPubKeyType = "pubkey",
      scriptPubKeyAddresses = None
    )
  }
}
