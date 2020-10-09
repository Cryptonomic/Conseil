package tech.cryptonomic.conseil.common.sql

import com.github.tminglei.slickpg.ExPostgresProfile
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities
import com.github.tminglei.slickpg.agg.PgAggFuncSupport

/** Custom postgres profile for enabling `insertOrUpdateAll` */
trait CustomProfileExtension extends ExPostgresProfile with PgAggFuncSupport {
  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  /** provides access to postgres general aggregations */
  lazy val generalAggregations = new GeneralAggFunctions {}

  /** provides access to postgres statistics aggregations */
  lazy val statisticsAggregations = new StatisticsAggFunctions {}

  /** provides access to postgres ordered-set aggregations */
  lazy val orderedSetAggregations = new OrderedSetAggFunctions {}

  /** provides access to postgres hypothetical-set aggregations */
  lazy val hypotheticalSetAggregations = new HypotheticalSetAggFunctions {}

}

object CustomProfileExtension extends CustomProfileExtension
