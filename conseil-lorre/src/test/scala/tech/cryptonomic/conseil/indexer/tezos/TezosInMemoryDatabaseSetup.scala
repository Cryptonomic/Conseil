package tech.cryptonomic.conseil.indexer.tezos

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabaseSetup
import tech.cryptonomic.conseil.common.tezos.Tables
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile.api._

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
      Fixture.table(Tables.BigMapContentsHistory),
      Fixture.table(Tables.OriginatedAccountMaps),
      Fixture.table(Tables.Bakers),
      Fixture.table(Tables.BakersCheckpoint),
      Fixture.table(Tables.BakersHistory),
      Fixture.table(Tables.RegisteredTokens),
      Fixture.table(Tables.TokenBalances),
      Fixture.table(Tables.BakingRights),
      Fixture.table(Tables.EndorsingRights),
      Fixture.table(Tables.Governance),
      Fixture.table(Tables.Forks)
    )
  )

  /* Custom scripts for tezos */
  override def initScripts: Seq[InitScript] = super.initScripts :+ customConstraintsScript

  /* We use this to restore the missing db structure that
   * slick-generated definitions have lost.
   * Specifically we need this to be able to relax FK constraints per session,
   * when running forks handling.
   * We correct the incorrect defaults used from postgres regarding
   * deferrable FKs.
   */
  val customConstraintsScript = new InitScript("", "") {

    //this is deliberately ignored
    override def create = DBIOAction.successful(())

    /* This adds the missing customization on db */
    override def customize = sqlu"""
SET search_path TO tezos;

ALTER TABLE accounts
ALTER CONSTRAINT accounts_block_id_fkey
DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operation_groups
ALTER CONSTRAINT block
DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE bakers
ALTER CONSTRAINT bakers_block_id_fkey
DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE baking_rights
ALTER CONSTRAINT bake_rights_block_fkey
DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE endorsing_rights
ALTER CONSTRAINT endorse_rights_block_fkey
DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operations
ALTER CONSTRAINT fk_blockhashes
DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE operations
ALTER CONSTRAINT fk_opgroups
DEFERRABLE INITIALLY IMMEDIATE;
""" andThen (DBIO.successful(()))
  }
}
