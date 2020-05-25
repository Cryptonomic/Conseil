package tech.cryptonomic.conseil.common.sql

import com.github.tminglei.slickpg.ExPostgresProfile
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities

/** Custom postgres profile for enabling `insertOrUpdateAll` */
trait CustomProfileExtension extends ExPostgresProfile {
  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate
}

object CustomProfileExtension extends CustomProfileExtension
