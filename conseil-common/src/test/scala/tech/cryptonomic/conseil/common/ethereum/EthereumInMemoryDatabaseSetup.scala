package tech.cryptonomic.conseil.common.ethereum

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup

// TODO: This trait is duplicated with conseil-api/src/test/scala/tech/cryptonomic/conseil/api/EthereumInMemoryDatabaseSetup.scala
//       It should be merged into one file, but it requires Tezos refactoring.
trait EthereumInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  registerSchema(
    "ethereum",
    Seq(
      Fixture.table(Tables.Blocks),
      Fixture.table(Tables.Transactions),
      Fixture.table(Tables.Receipts),
      Fixture.table(Tables.Logs),
      Fixture.table(Tables.Contracts),
      Fixture.table(Tables.Tokens),
      Fixture.table(Tables.TokenTransfers),
      Fixture.view(Views.AccountsViewSql)
    )
  )
}
