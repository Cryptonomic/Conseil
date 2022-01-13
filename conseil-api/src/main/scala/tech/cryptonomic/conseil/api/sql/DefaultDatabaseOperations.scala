package tech.cryptonomic.conseil.api.sql

import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext

object DefaultDatabaseOperations {

  /**
    * Counts number of rows in the given table
    *
    * Note that this is only estimation,
    * which is mostly used by planner and updated during VACUUM or ANALYZE
    *
    * @param schema name of the schema
    * @param table  slick table
    * @return       amount of rows in the table
    */
  def countRows(schema: String, table: String)(implicit ec: ExecutionContext): DBIO[Int] =
    sql"""SELECT reltuples FROM pg_class WHERE oid = '#${schema + "." + table}'::regclass;"""
      .as[Int]
      .map(_.head)

  /**
    * Counts number of distinct elements by given table and column
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param schema name of the schema
    * @param table  name of the table
    * @param column name of the column
    * @return       amount of distinct elements in given column
    */
  def countDistinct(schema: String, table: String, column: String)(implicit ec: ExecutionContext): DBIO[Int] =
    sql"""SELECT COUNT(*) FROM (SELECT DISTINCT #$column FROM #${schema + "." + table}) AS temp"""
      .as[Int]
      .map(_.head)

  /**
    * Selects distinct elements by given table and column
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param schema name of the schema
    * @param table  name of the table
    * @param column name of the column
    * @return       distinct elements in given column as a list
    */
  def selectDistinct(schema: String, table: String, column: String)(implicit ec: ExecutionContext): DBIO[List[String]] =
    sql"""SELECT DISTINCT #$column::VARCHAR FROM #${schema + "." + table} WHERE #$column IS NOT NULL"""
      .as[String]
      .map(_.toList)

  /**
    * Selects distinct elements by given table and column with filter
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param schema         name of the schema
    * @param table          name of the table
    * @param column         name of the column
    * @param matchingString string which is being matched
    * @return               distinct elements in given column as a list
    */
  def selectDistinctLike(schema: String, table: String, column: String, matchingString: String)(implicit
      ec: ExecutionContext
  ): DBIO[List[String]] =
    sql"""SELECT DISTINCT #$column::VARCHAR FROM #${schema + "." + table} WHERE #$column LIKE '%#$matchingString%' AND #$column IS NOT NULL"""
      .as[String]
      .map(_.toList)

}
