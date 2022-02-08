package tech.cryptonomic.conseil.indexer.tezos.forks

import tech.cryptonomic.conseil.common.tezos.TezosTypes.{BlockLevel, TezosBlockHash}
import tech.cryptonomic.conseil.indexer.tezos.{TezosDatabaseOperations => DBOps}
import tech.cryptonomic.conseil.indexer.forks.ForkAmender

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._
import cats.implicits._
import slickeffect.implicits._
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport

/** Provides static utilities */
object TezosForkInvalidatingAmender {
  def apply(db: Database)(implicit ec: ExecutionContext): TezosForkInvalidatingAmender =
    new TezosForkInvalidatingAmender(db)
}

/** Defines the actual logic that amends any data that is identified
  * on a fork no longer valid.
  * This implementation will mark such data as invalidated, for all entities
  * that supports it via fields.
  * Other data will be simply removed, if no real information loss is implied
  * E.g. removing processed chain events from the db registry.
  */
class TezosForkInvalidatingAmender(db: Database)(implicit ec: ExecutionContext)
    extends ForkAmender[Future, TezosBlockHash]  with ConseilLogSupport {

  /* Note that we need to defer constraint checks manually with postgres
   * policies on consistency levels.
   * Since we need to break constraints between different tables referring
   * to the blocks being invalidated, until all entries are invalidated consistently,
   * we tell the db to wait until the transaction session commits before running any verification.
   */

  override def amendFork(
      forkLevel: BlockLevel,
      forkedBlockId: TezosBlockHash,
      indexedHeadLevel: BlockLevel,
      detectionTime: Instant
  ): Future[(String, Int)] = {
    val forkAndInvalidateAction = for {
      forkId <- DBOps.writeForkEntry(
        forkLevel,
        forkedBlockId,
        indexedHeadLevel,
        detectionTime
      )
      _ = logger.info(s"Wrote fork with ID $forkId")
      _ <- DBOps.deferConstraints()
      _ = logger.info(s"Defered constraints")
      blocks <- invalidateBlocks(forkLevel, detectionTime, forkId)
      invalidated <- invalidateData(forkLevel, detectionTime, forkId)
      _ = logger.info(s"Invalidated data $invalidated")
    } yield (forkId, invalidated + blocks)

    db.run(forkAndInvalidateAction.transactionally)
  }

  private def invalidateBlocks(forkLevel: BlockLevel, asOf: Instant, forkId: String) =
    DBOps.ForkInvalidation.blocks.invalidate(forkLevel, asOf, forkId)

  /* run invalidation on db across all impacted tables
   * The powerful combinator foldA uses the fact that
   * - a List can be folded over, that is, given a combinator for each pair of elements,
   *   it can be reduced to a single value, by recursively applying the combinator (e.g. i + j)
   * - Ints can be combined by default with the sum monoid for ints: it simply adds the
   *   values together to get the sum
   * - DBIO has an Applicative instance which allows the List to act "internally" to
   *   the DBIO action, recursively on each pair... so that the List folding can happen
   *   inside the DBIO wrapper to result in a single value.
   * This means we're able to immediately convert a List[DBIO[Int]] => DBIO[Int]
   */
  private def invalidateData(forkLevel: BlockLevel, asOf: Instant, forkId: String)(
      implicit ec: ExecutionContext
  ): DBIO[Int] =
    List(
      DBOps.ForkInvalidation.operationGroups.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.operations.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.accounts.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.accountsHistory.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.bakers.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.bakersHistory.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.bakingRights.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.endorsingRights.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.fees.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.governance.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.tokenBalances.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.bigMaps.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.bigMapContents.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.bigMapContentsHistory.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.originatedAccountMaps.invalidate(forkLevel, asOf, forkId),
      DBOps.ForkInvalidation.deleteProcessedEvents(forkLevel)
    ).foldA
}
