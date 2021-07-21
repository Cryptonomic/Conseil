package tech.cryptonomic.conseil.indexer.sql

import slick.dbio.CleanUpAction
import slick.jdbc.PostgresProfile.api._
import slickeffect.implicits._
import slick.lifted.AbstractTable

import scala.concurrent.ExecutionContext

object DefaultDatabaseOperations {
  import tech.cryptonomic.conseil.common.sql.CustomProfileExtension.api._

  /** Inserts to the table if table is empty
    * @param table slick TableQuery[_] to which we want to insert
    * @param rows rows to be added
    * @return the number of entries saved
    */
  def insertWhenEmpty[A <: AbstractTable[_]](
      table: TableQuery[A],
      rows: List[A#TableElementType],
      upsert: Boolean
  )(implicit ec: ExecutionContext): DBIO[Option[Int]] = {
    if(upsert) {
      table.insertOrUpdateAll(rows)
    } else {
      table.exists.result.flatMap {
        case true => DBIO.successful(Some(0))
        case false => table ++= rows
      }
    }
  }

}
