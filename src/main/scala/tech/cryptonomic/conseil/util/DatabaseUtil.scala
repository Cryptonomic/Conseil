package tech.cryptonomic.conseil.util

import slick.jdbc.PostgresProfile.api._

object DatabaseUtil {
  lazy val db = Database.forConfig("conseildb")


}
