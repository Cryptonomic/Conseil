package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.common.bitcoin.Tables
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup

trait BitcoinInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  registerSchema(
    "bitcoin",
    Seq(
      Fixture(Tables.Blocks),
      Fixture(Tables.Transactions)
      //TODO Uncomment below tables, once the issue with PGArray support will be solved
//      Fixture(Tables.Inputs),
//      Fixture(Tables.Outputs)
    )
  )
}
