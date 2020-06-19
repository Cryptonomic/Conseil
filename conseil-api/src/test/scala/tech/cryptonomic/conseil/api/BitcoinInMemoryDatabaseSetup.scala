package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.common.bitcoin.Tables
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
