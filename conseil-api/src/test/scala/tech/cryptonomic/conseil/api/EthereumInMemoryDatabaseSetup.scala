package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.common.ethereum.{Tables, Views}
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup

trait EthereumInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  registerSchema(
    "ethereum",
    Seq(
      Fixture.table(Tables.Blocks),
      Fixture.table(Tables.Transactions),
      Fixture.table(Tables.Receipts),
      Fixture.table(Tables.Logs),
      Fixture.table(Tables.Tokens),
      Fixture.table(Tables.TokenTransfers),
      Fixture.view(Views.AccountsViewSql)
    )
  )
}
