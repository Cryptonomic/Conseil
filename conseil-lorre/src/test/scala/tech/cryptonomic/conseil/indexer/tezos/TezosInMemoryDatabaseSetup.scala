package tech.cryptonomic.conseil.indexer.tezos

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup
import tech.cryptonomic.conseil.common.tezos.Tables

trait TezosInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  registerSchema(
    "tezos",
    Seq(
      Fixture(Tables.Blocks),
      Fixture(Tables.OperationGroups),
      Fixture(Tables.Operations),
      Fixture(Tables.BalanceUpdates),
      Fixture(Tables.Accounts),
      Fixture(Tables.Fees),
      Fixture(Tables.AccountsCheckpoint),
      Fixture(Tables.AccountsHistory),
      Fixture(Tables.ProcessedChainEvents),
      Fixture(Tables.BigMaps),
      Fixture(Tables.BigMapContents),
      Fixture(Tables.OriginatedAccountMaps),
      Fixture(Tables.Bakers),
      Fixture(Tables.BakersCheckpoint),
      Fixture(Tables.BakersHistory),
      Fixture(Tables.RegisteredTokens),
      Fixture(Tables.TokenBalances)
    )
  )
}
