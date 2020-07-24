package tech.cryptonomic.conseil.common.ethereum

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup

// TODO: This trait is duplicated with conseil-api/src/test/scala/tech/cryptonomic/conseil/api/EthereumInMemoryDatabaseSetup.scala
//       It should be merged into one file, but it requires Tezos refactoring.
trait EthereumInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  registerSchema(
    "ethereum",
    Seq(
      Fixture(Tables.Blocks),
      Fixture(Tables.Transactions)
    )
  )
}
