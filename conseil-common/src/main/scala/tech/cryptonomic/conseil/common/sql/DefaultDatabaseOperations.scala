package tech.cryptonomic.conseil.common.sql

import slick.lifted.AbstractTable
import tech.cryptonomic.conseil.common.generic.chain
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.util.ConfigUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object DefaultDatabaseOperations extends ConseilLogSupport {

  import tech.cryptonomic.conseil.common.sql.CustomProfileExtension.api._
  import kantan.csv._
  import shapeless._
  import shapeless.ops.hlist._

  /** Reads and inserts CSV file to the database for the given table.
    * Also Gives possibility to upsert when table is already filled with data
    */
  def initTableFromCsv[A <: AbstractTable[_], H <: HList](
      db: Database,
      table: TableQuery[A],
      platform: String,
      network: String,
      separator: Char = ',',
      upsert: Boolean = false
  )(implicit
      hd: HeaderDecoder[A#TableElementType],
      g: Generic.Aux[A#TableElementType, H],
      m: Mapper.Aux[ConfigUtil.Csv.Trimmer.type, H, H],
      ec: ExecutionContext
  ): Future[(List[A#TableElementType], Option[Int])] =
    ConfigUtil.Csv.readTableRowsFromCsv(table, platform, network, separator) match {
      case Some(rows) =>
        db.run(insertWhenEmpty(table, rows, upsert))
          .andThen {
            case Success(_) => logger.info(s"Written ${rows.size} ${table.baseTableRow.tableName} rows")
            case Failure(e) => logger.error(s"Could not fill ${table.baseTableRow.tableName} table", e)
          }
          .map(rows -> _)
      case None =>
        logger.warn(s"No csv configuration found to initialize table ${table.baseTableRow.tableName} for $network.")
        Future.successful(List.empty -> None)
    }

  /** Inserts to the table if table is empty
    *
    * @param table slick TableQuery[_] to which we want to insert
    * @param rows  rows to be added
    * @return the number of entries saved
    */
  def insertWhenEmpty[A <: AbstractTable[_]](
      table: TableQuery[A],
      rows: List[A#TableElementType],
      upsert: Boolean
  )(implicit ec: ExecutionContext): DBIO[Option[Int]] =
    if (upsert) {
      table.insertOrUpdateAll(rows)
    } else {
      table.exists.result.flatMap {
        case true => DBIO.successful(Some(0))
        case false => table ++= rows
      }
    }

}
