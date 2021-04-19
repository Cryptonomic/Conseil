package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.common.ethereum.Tables
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup

trait EthereumInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  registerSchema(
    "ethereum",
    Seq(
      Fixture.table(Tables.Blocks),
      Fixture.table(Tables.Transactions),
      Fixture.table(Tables.Receipts),
      Fixture.table(Tables.Logs),
      Fixture.table(Tables.TokenTransfers),
      Fixture.table(Tables.TokensHistory),
      Fixture.table(Tables.Accounts),
      Fixture.table(Tables.AccountsHistory)
    )
  )
}
