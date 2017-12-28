package tech.cryptonomic.conseil.util

import slick.jdbc.PostgresProfile.api._

/**
  * Utility functions and members for common database operations.
  */
object DatabaseUtil {
  lazy val db = Database.forConfig("conseildb")
}
