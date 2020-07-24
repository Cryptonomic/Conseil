package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.common.bitcoin.{Tables, Views}
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup

trait BitcoinInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  registerSchema(
    "bitcoin",
    Seq(
      Fixture.table(Tables.Blocks),
      Fixture.table(Tables.Transactions),
      Fixture.table(Tables.Inputs),
      Fixture.table(Tables.Outputs),
      Fixture.view(Views.AccountsViewSql)
    )
  )
}
