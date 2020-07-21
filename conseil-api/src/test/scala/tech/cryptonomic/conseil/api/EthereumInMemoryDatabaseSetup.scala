package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.common.ethereum.Tables
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup

trait EthereumInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  registerSchema(
    "ethereum",
    Seq(
      Fixture(Tables.Blocks),
      Fixture(Tables.Transactions),
      Fixture(Tables.Logs)
    )
  )
}
