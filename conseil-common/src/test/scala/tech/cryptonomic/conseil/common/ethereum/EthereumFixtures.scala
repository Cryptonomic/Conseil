package tech.cryptonomic.conseil.common.ethereum

import java.time.Instant
import java.sql.Timestamp

import tech.cryptonomic.conseil.common.ethereum.rpc.json._

/**
  * Fixtures for Ethereum based on MainNet block at height 0x18E70 with hash 0x017685281a11f6514538b113d62c7efb9852922ff308f4596d2c37c6f4717214
  */
trait EthereumFixtures {

  /**
    * Fixtures containing raw json responses from the JSON-RPC server.
    *
    * They can be used to stub http4s client in a test. Like this:
    *
    * {{{
    *   import cats.effect.IO
    *   import org.http4s.Response
    *   import org.http4s.client.Client
    *   import fs2.Stream
    *
    *   val response = Response[IO](
    *     Status.Ok,
    *     body = Stream(JsonFixtures.nameOfTheMethodResponse).through(fs2.text.utf8Encode)
    *   )
    *   val httpClient = Client.fromHttpApp(HttpApp.liftF(IO.pure(response)))
    * }}}
    */
  object JsonFixtures {

    // Json result for the: eth_blockNumber
    val ethBlockNumberResponse =
      """[{
      |  "result": "0x4b7",
      |  "id": "requestId"
      |}]""".stripMargin

    // Json result for the: eth_getBlockByNumber
    val getBlockResponse =
      """[{
      |  "result": {
      |    "difficulty": "0x390cd208cb4",
      |    "extraData": "0x476574682f76312e302e312d38326566323666362f6c696e75782f676f312e34",
      |    "gasLimit": "0x2fefd8",
      |    "gasUsed": "0x5208",
      |    "hash": "0x017685281a11f6514538b113d62c7efb9852922ff308f4596d2c37c6f4717214",
      |    "logsBloom": "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
      |    "miner": "0xe6a7a1d47ff21b6321162aea7c6cb457d5476bca",
      |    "mixHash": "0x4a681d95af6bbfc6b36cf2d2f231b396ece03f602ffacde518e67bf919527f57",
      |    "nonce": "0xee4105b65d161083",
      |    "number": "0x18e70",
      |    "parentHash": "0xd7a6cfddb3615bf35206461b08cca260f1514b4352e899fa3cf723b6d84f02b3",
      |    "receiptsRoot": "0x28a7d7fd6bf741b28dee4232e7189675ce34af4c331c989637a337fab15a817a",
      |    "sha3Uncles": "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
      |    "size": "0x296",
      |    "stateRoot": "0x8f60c479b2a111e26e31689e8c3405c7e822df1562de6bd8e53c4a8e344447f4",
      |    "timestamp": "0x55d21481",
      |    "totalDifficulty": "0x275495634ba8c02",
      |    "transactions": [
      |      "0x3cfcdc56f1ecf4aef8b95dddc9f5b727593b56238bfad7b1932efdfdf9e49fcd"
      |    ],
      |    "transactionsRoot": "0x537ffda8486bb3e00e1b73dc129f2cc4dd2b224725645f2983480dfb368064fe",
      |    "uncles": []
      |  },
      |  "id": "requestId"
      |}]""".stripMargin

    // Json result for the: eth_getTransactionByHash
    val getTransactionByHashResponse =
      """[{
      |  "result": {
      |    "blockHash": "0x017685281a11f6514538b113d62c7efb9852922ff308f4596d2c37c6f4717214",
      |    "blockNumber": "0x18e70",
      |    "from": "0xd9666150a9da92d9108198a4072970805a8b3428",
      |    "gas": "0x5208",
      |    "gasPrice": "0xce52b27b5",
      |    "hash": "0x3cfcdc56f1ecf4aef8b95dddc9f5b727593b56238bfad7b1932efdfdf9e49fcd",
      |    "input": "0x",
      |    "nonce": "0x36",
      |    "r": "0xec5b17e102048e5e52ab779f4ab757634c5aa009dd595aa9575bd55e8d2b7d87",
      |    "s": "0x7021d4517c8a5f92872a7b24d59d47631a5dfa2f5fe340708fc50abf2c817345",
      |    "to": "0x32be343b94f860124dc4fee278fdcbd38c102d88",
      |    "transactionIndex": "0x0",
      |    "v": "0x1b",
      |    "value": "0x455f6fb3eec11400"
      |  },
      |  "id": "requestId"
      |}]""".stripMargin
  }

  /**
    * Fixtures containing decoded json responses form the JSON-RPC server.
    *
    * They can be used to check the desired result in [[EthereumClient]]. Like this:
    *
    * {{{
    *   import cats.effect.IO
    *
    *   val ethereumClient = EthereumClient[IO](...)
    *
    *   ethereumClient.getMostRecentBlockNumber.compile.toList.unsafeRunSync() shouldBe List(
    *     RpcFixtures.blockchainInfoResult
    *   )
    * }}}
    */
  object RpcFixtures {
    val blockResult = Block(
      number = "0x18e70",
      hash = "0x017685281a11f6514538b113d62c7efb9852922ff308f4596d2c37c6f4717214",
      parentHash = Some("0xd7a6cfddb3615bf35206461b08cca260f1514b4352e899fa3cf723b6d84f02b3"),
      nonce = "0xee4105b65d161083",
      sha3Uncles = "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
      logsBloom =
        "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
      transactionsRoot = "0x537ffda8486bb3e00e1b73dc129f2cc4dd2b224725645f2983480dfb368064fe",
      stateRoot = "0x8f60c479b2a111e26e31689e8c3405c7e822df1562de6bd8e53c4a8e344447f4",
      receiptsRoot = "0x28a7d7fd6bf741b28dee4232e7189675ce34af4c331c989637a337fab15a817a",
      miner = "0xe6a7a1d47ff21b6321162aea7c6cb457d5476bca",
      mixHash = "0x4a681d95af6bbfc6b36cf2d2f231b396ece03f602ffacde518e67bf919527f57",
      difficulty = "0x390cd208cb4",
      totalDifficulty = "0x275495634ba8c02",
      extraData = "0x476574682f76312e302e312d38326566323666362f6c696e75782f676f312e34",
      size = "0x296",
      gasLimit = "0x2fefd8",
      gasUsed = "0x5208",
      timestamp = "0x55d21481",
      transactions = List("0x3cfcdc56f1ecf4aef8b95dddc9f5b727593b56238bfad7b1932efdfdf9e49fcd"),
      uncles = Nil
    )

    val transactionResult = Transaction(
      blockHash = "0x017685281a11f6514538b113d62c7efb9852922ff308f4596d2c37c6f4717214",
      blockNumber = "0x18e70",
      from = "0xd9666150a9da92d9108198a4072970805a8b3428",
      gas = "0x5208",
      gasPrice = "0xce52b27b5",
      hash = "0x3cfcdc56f1ecf4aef8b95dddc9f5b727593b56238bfad7b1932efdfdf9e49fcd",
      input = "0x",
      nonce = "0x36",
      to = "0x32be343b94f860124dc4fee278fdcbd38c102d88",
      transactionIndex = "0x0",
      value = "0x455f6fb3eec11400",
      v = "0x1b",
      r = "0xec5b17e102048e5e52ab779f4ab757634c5aa009dd595aa9575bd55e8d2b7d87",
      s = "0x7021d4517c8a5f92872a7b24d59d47631a5dfa2f5fe340708fc50abf2c817345"
    )

    val logResult = Log(
      address = "0xdac17f958d2ee523a2206206994597c13d831ec7",
      blockHash = "0x017685281a11f6514538b113d62c7efb9852922ff308f4596d2c37c6f4717214",
      blockNumber = "0x18e70",
      data = "0x000000000000000000000000000000000000000000000000000000003b9aca00",
      logIndex = "0x0",
      removed = false,
      topics = List(
        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
        "0x000000000000000000000000fdb16996831753d5331ff813c29a93c76834a0ad",
        "0x0000000000000000000000000f4a253ee5a63bb4b23a360ad52d69c86bc9fe97"
      ),
      transactionHash = "0x808dc2cefe4e26c7bac2262930497cfcc20c37729cb3eaa8517fbf76b08a52c7",
      transactionIndex = "0x3"
    )
  }

  /**
    * Fixtures containing Slick rows.
    */
  object DbFixtures {
    val blockRow = Tables.BlocksRow(
      hash = "0x017685281a11f6514538b113d62c7efb9852922ff308f4596d2c37c6f4717214",
      number = 102000,
      difficulty = "0x390cd208cb4",
      extraData = "0x476574682f76312e302e312d38326566323666362f6c696e75782f676f312e34",
      gasLimit = "0x2fefd8",
      gasUsed = "0x5208",
      logsBloom =
        "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
      miner = "0xe6a7a1d47ff21b6321162aea7c6cb457d5476bca",
      mixHash = "0x4a681d95af6bbfc6b36cf2d2f231b396ece03f602ffacde518e67bf919527f57",
      nonce = "0xee4105b65d161083",
      parentHash = Some("0xd7a6cfddb3615bf35206461b08cca260f1514b4352e899fa3cf723b6d84f02b3"),
      receiptsRoot = "0x28a7d7fd6bf741b28dee4232e7189675ce34af4c331c989637a337fab15a817a",
      sha3Uncles = "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
      size = "0x296",
      stateRoot = "0x8f60c479b2a111e26e31689e8c3405c7e822df1562de6bd8e53c4a8e344447f4",
      totalDifficulty = "0x275495634ba8c02",
      transactionsRoot = "0x537ffda8486bb3e00e1b73dc129f2cc4dd2b224725645f2983480dfb368064fe",
      uncles = None,
      timestamp = Timestamp.from(Instant.parse("2015-08-17T17:06:09.00Z"))
    )

    val transactionRow = Tables.TransactionsRow(
      hash = "0x3cfcdc56f1ecf4aef8b95dddc9f5b727593b56238bfad7b1932efdfdf9e49fcd",
      blockHash = "0x017685281a11f6514538b113d62c7efb9852922ff308f4596d2c37c6f4717214",
      blockNumber = 102000,
      from = "0xd9666150a9da92d9108198a4072970805a8b3428",
      gas = "0x5208",
      gasPrice = "0xce52b27b5",
      input = "0x",
      nonce = "0x36",
      to = "0x32be343b94f860124dc4fee278fdcbd38c102d88",
      transactionIndex = "0x0",
      value = "0x455f6fb3eec11400",
      v = "0x1b",
      r = "0xec5b17e102048e5e52ab779f4ab757634c5aa009dd595aa9575bd55e8d2b7d87",
      s = "0x7021d4517c8a5f92872a7b24d59d47631a5dfa2f5fe340708fc50abf2c817345"
    )

    val logRow = Tables.LogsRow(
      address = "0xdac17f958d2ee523a2206206994597c13d831ec7",
      blockHash = "0x017685281a11f6514538b113d62c7efb9852922ff308f4596d2c37c6f4717214",
      blockNumber = 102000,
      data = "0x000000000000000000000000000000000000000000000000000000003b9aca00",
      logIndex = "0x0",
      removed = false,
      topics =
        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef,0x000000000000000000000000fdb16996831753d5331ff813c29a93c76834a0ad,0x0000000000000000000000000f4a253ee5a63bb4b23a360ad52d69c86bc9fe97",
      transactionHash = "0x808dc2cefe4e26c7bac2262930497cfcc20c37729cb3eaa8517fbf76b08a52c7",
      transactionIndex = "0x3"
    )
  }
}
