package tech.cryptonomic.conseil.indexer.tezos

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup
import tech.cryptonomic.conseil.common.tezos.Tables

trait TezosInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  registerSchema(
    "tezos",
    Seq(
      Fixture.table(Tables.Blocks),
      Fixture.table(Tables.OperationGroups),
      Fixture.table(Tables.Operations),
      Fixture.table(Tables.BalanceUpdates),
      Fixture.table(Tables.Accounts),
      Fixture.table(Tables.Fees),
      Fixture.table(Tables.AccountsCheckpoint),
      Fixture.table(Tables.AccountsHistory),
      Fixture.table(Tables.ProcessedChainEvents),
      Fixture.table(Tables.BigMaps),
      Fixture.table(Tables.BigMapContents),
      Fixture.table(Tables.OriginatedAccountMaps),
      Fixture.table(Tables.Bakers),
      Fixture.table(Tables.BakersCheckpoint),
      Fixture.table(Tables.BakersHistory),
      Fixture.table(Tables.RegisteredTokens),
      Fixture.table(Tables.TokenBalances),
      Fixture.table(Tables.BakingRights),
      Fixture.table(Tables.EndorsingRights),
      Fixture.table(Tables.Governance)
    )
  )
}
