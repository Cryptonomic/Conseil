package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup
import tech.cryptonomic.conseil.common.tezos.Tables

//TODO This class is a duplicate from conseil-common,
// which will be updated once entire project will be split properly
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
  )
}
