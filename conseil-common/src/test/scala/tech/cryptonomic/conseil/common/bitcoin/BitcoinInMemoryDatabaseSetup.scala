package tech.cryptonomic.conseil.common.bitcoin

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup

// TODO: This trait is duplicated with conseil-api/src/test/scala/tech/cryptonomic/conseil/api/BitcoinInMemoryDatabaseSetup.scala
//       It should be merged into one file, but it requires Tezos refactoring.
trait BitcoinInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  registerSchema(
    "bitcoin",
    Seq(
      Fixture(Tables.Blocks),
      Fixture(Tables.Transactions),
      Fixture(Tables.Inputs),
      Fixture(Tables.Outputs)
    )
  )
}
