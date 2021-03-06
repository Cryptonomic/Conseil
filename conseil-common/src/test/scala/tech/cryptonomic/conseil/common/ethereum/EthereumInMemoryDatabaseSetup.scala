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
      Fixture.table(Tables.TokenTransfers),
      Fixture.table(Tables.TokensHistory),
      Fixture.table(Tables.Accounts),
      Fixture.table(Tables.AccountsHistory)
    )
  )
}
