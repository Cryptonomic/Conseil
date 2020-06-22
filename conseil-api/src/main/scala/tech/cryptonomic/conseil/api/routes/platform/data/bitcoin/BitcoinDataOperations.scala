package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.common.util.DatabaseUtil

class BitcoinDataOperations extends ApiDataOperations {
  override lazy val dbReadHandle: Database = DatabaseUtil.conseilDb
}
