package tech.cryptonomic.conseil.common.sql

import com.github.tminglei.slickpg._
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities

/** Custom postgres profile for enabling `insertOrUpdateAll` */
trait CustomProfileExtension extends ExPostgresProfile with PgArraySupport {
  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val api = ConseilAPI
  object ConseilAPI extends API with ArrayImplicits
}

object CustomProfileExtension extends CustomProfileExtension
