package tech.cryptonomic.conseil.common.tezos

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup

trait TezosInMemoryDatabaseSetup extends InMemoryDatabaseSetup {
  override val schema: String = "tezos"
  override val fixtures: Seq[Fixture[_]] = Seq(
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
    Fixture(Tables.RegisteredTokens),
    Fixture(Tables.TokenBalances)
  )
}
