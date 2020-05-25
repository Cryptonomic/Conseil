package tech.cryptonomic.conseil.common.sql

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{AbstractTable, TableQuery}

import scala.concurrent.ExecutionContext

/** Provides basic SQL operations on Postgres database */
case class DefaultDatabaseOperations(prefix: String) {

  /** Inserts to the table if table is empty
    * @param table slick TableQuery[_] to which we want to insert
    * @param rows rows to be added
    * @return the number of entries saved
    */
  def insertWhenEmpty[A <: AbstractTable[_]](
      table: TableQuery[A],
      rows: List[A#TableElementType]
  )(implicit ec: ExecutionContext): DBIO[Option[Int]] =
    table.exists.result.flatMap {
      case true => DBIO.successful(Some(0))
      case false => table ++= rows
    }

  /**
    * Counts number of rows in the given table
    * @param table  slick table
    * @return       amount of rows in the table
    */
  def countRows(table: String)(implicit ec: ExecutionContext): DBIO[Int] =
    sql"""SELECT reltuples FROM pg_class WHERE relname = $table"""
      .as[Int]
      .map(_.head)

  /**
    * Counts number of distinct elements by given table and column
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table  name of the table
    * @param column name of the column
    * @return       amount of distinct elements in given column
    */
  def countDistinct(table: String, column: String)(implicit ec: ExecutionContext): DBIO[Int] =
    sql"""SELECT COUNT(*) FROM (SELECT DISTINCT #$column FROM #${prefix + "." + table}) AS temp"""
      .as[Int]
      .map(_.head)

  /**
    * Selects distinct elements by given table and column
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table  name of the table
    * @param column name of the column
    * @return       distinct elements in given column as a list
    */
  def selectDistinct(table: String, column: String)(implicit ec: ExecutionContext): DBIO[List[String]] =
    sql"""SELECT DISTINCT #$column::VARCHAR FROM #${prefix + "." + table} WHERE #$column IS NOT NULL"""
      .as[String]
      .map(_.toList)

  /**
    * Selects distinct elements by given table and column with filter
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table          name of the table
    * @param column         name of the column
    * @param matchingString string which is being matched
    * @return               distinct elements in given column as a list
    */
  def selectDistinctLike(table: String, column: String, matchingString: String)(
      implicit ec: ExecutionContext
  ): DBIO[List[String]] =
    sql"""SELECT DISTINCT #$column::VARCHAR FROM #${prefix + "." + table} WHERE #$column LIKE '%#$matchingString%' AND #$column IS NOT NULL"""
      .as[String]
      .map(_.toList)

}
