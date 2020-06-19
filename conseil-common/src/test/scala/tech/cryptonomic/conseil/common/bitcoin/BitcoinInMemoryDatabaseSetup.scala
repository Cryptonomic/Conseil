package tech.cryptonomic.conseil.common.bitcoin

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup

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
